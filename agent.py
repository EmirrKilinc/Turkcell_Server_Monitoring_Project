"""
Sistem Izleme PoC - Python Agent
HMAC-SHA256 imzali metrik gonderimi + secret key iptalinde kendi kendini sonlandirma (self-destruct).
Konfigurasyon /data01/monitoring/agent_config.json dosyasindan okunur (SSH provisioning sirasinda yazilir).

Ozel metrikler (custom metrics) tamamen bu ayni disa-dogru heartbeat kanali
uzerinden calisir: agent hicbir zaman disaridan baglanti kabul etmez (dinleyen
port yok), bunun yerine periyodik olarak merkeze "ne calistirmaliyim?" diye
sorar (fetch_sync) ve sonuclari bir sonraki dongude geri postlar (push_results).
Bu, "Fetch Now" (anlik calistirma) dahil her seyi ayni guvenlik modeliyle
(kalici SSH kimlik bilgisi yok, gelen baglanti yok) calistirmayi saglar.
"""

import hashlib
import hmac
import json
import logging
import os
import re
import shlex
import socket
import subprocess
import sys
import time

import psutil
import requests

CONFIG_PATH = os.environ.get("AGENT_CONFIG_PATH", "/data01/monitoring/agent_config.json")
LOG_PATH = os.environ.get("AGENT_LOG_PATH", "/data01/monitoring/agent.log")
SEND_INTERVAL_SECONDS = 5
SYNC_EVERY_N_LOOPS = 3  # ~15s at the default 5s heartbeat, matching MetricGroup's default interval_seconds
REQUEST_TIMEOUT_SECONDS = 3
MAX_CONSECUTIVE_FORBIDDEN = 3

logger = logging.getLogger("agent")


def configure_logging(log_path: str = LOG_PATH) -> None:
    """Deferred on purpose: importing this module (e.g. from tests) must not
    touch the filesystem. Only run() calls this, right before it needs it."""
    os.makedirs(os.path.dirname(log_path), exist_ok=True)
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(message)s",
        handlers=[logging.FileHandler(log_path, mode="a"), logging.StreamHandler(sys.stdout)],
    )


def load_config(path: str = CONFIG_PATH) -> dict:
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def collect_metrics(hostname: str) -> dict:
    return {
        "hostname": hostname,
        "cpuPercent": psutil.cpu_percent(interval=1),
        "ramPercent": psutil.virtual_memory().percent,
        "diskPercent": psutil.disk_usage("/").percent,
    }


def sign_payload(secret_key: str, timestamp: int, body_json: str) -> str:
    message = f"{timestamp}{body_json}".encode("utf-8")
    return hmac.new(secret_key.encode("utf-8"), message, hashlib.sha256).hexdigest()


def _signed_headers(secret_key: str, hostname: str, timestamp: int, body_json: str) -> dict:
    signature = sign_payload(secret_key, timestamp, body_json)
    return {
        "Content-Type": "application/json",
        "X-Timestamp": str(timestamp),
        "X-Signature": signature,
        "X-Server-Hostname": hostname,
    }


def send_metrics(ingest_url: str, secret_key: str, hostname: str, payload: dict) -> int:
    body_json = json.dumps(payload, separators=(",", ":"))
    timestamp = int(time.time())
    headers = _signed_headers(secret_key, hostname, timestamp, body_json)

    response = requests.post(ingest_url, data=body_json, headers=headers, timeout=REQUEST_TIMEOUT_SECONDS)
    return response.status_code


def fetch_sync(sync_url: str, secret_key: str, hostname: str) -> dict:
    """GET has no body, so the signature covers timestamp + "" - matches
    HmacAuthFilter's server-side computation for an empty request body."""
    timestamp = int(time.time())
    headers = _signed_headers(secret_key, hostname, timestamp, "")
    response = requests.get(sync_url, headers=headers, timeout=REQUEST_TIMEOUT_SECONDS)
    response.raise_for_status()
    return response.json()


def push_results(results_url: str, secret_key: str, hostname: str, results_payload: dict) -> int:
    body_json = json.dumps(results_payload, separators=(",", ":"))
    timestamp = int(time.time())
    headers = _signed_headers(secret_key, hostname, timestamp, body_json)
    response = requests.post(results_url, data=body_json, headers=headers, timeout=REQUEST_TIMEOUT_SECONDS)
    return response.status_code


def process_response(status_code: int, consecutive_forbidden: int) -> tuple[int, bool]:
    """Returns (new_consecutive_forbidden_count, should_terminate)."""
    if status_code == 403:
        consecutive_forbidden += 1
        return consecutive_forbidden, consecutive_forbidden >= MAX_CONSECUTIVE_FORBIDDEN
    return 0, False


# ==========================================
# Custom metric collectors - one function per MetricType. Every collector
# returns {"success": bool, "value": ..., "error": str|None} and is only ever
# called through execute_metric_item(), which guarantees no exception from a
# bad definition (malformed payload, missing binary, unreachable host, ...)
# can ever escape and kill the loop.
# ==========================================

def _coerce_number(text: str):
    text = text.strip()
    try:
        if "." in text:
            return float(text)
        return int(text)
    except (TypeError, ValueError):
        return text


def _extract_number(text: str, pattern: str):
    match = re.search(pattern, text)
    if not match:
        return None
    return _coerce_number(match.group(1))


def _collect_http_endpoint(payload: dict) -> dict:
    url = payload["url"]
    method = payload.get("method", "GET").upper()
    timeout = payload.get("timeoutSeconds", REQUEST_TIMEOUT_SECONDS)
    expected_status = payload.get("expectedStatus")
    extract_pattern = payload.get("extractPattern")
    value_type = payload.get("valueType", "raw")

    response = requests.request(method, url, timeout=timeout)

    if expected_status is not None and response.status_code != expected_status:
        return {"success": False, "value": None, "error": f"beklenmeyen HTTP durumu: {response.status_code}"}

    if value_type == "bool":
        return {"success": True, "value": True, "error": None}

    if extract_pattern:
        value = _extract_number(response.text, extract_pattern)
        if value is None:
            return {"success": False, "value": None, "error": "extractPattern eslesmedi"}
        return {"success": True, "value": value, "error": None}

    return {"success": True, "value": response.text.strip(), "error": None}


def _collect_port_check(payload: dict) -> dict:
    host = payload["host"]
    port = int(payload["port"])
    timeout = payload.get("timeoutSeconds", REQUEST_TIMEOUT_SECONDS)
    probe = payload.get("probe")
    expect = payload.get("expect")

    with socket.create_connection((host, port), timeout=timeout) as sock:
        if probe:
            sock.sendall(probe.encode("utf-8"))
            sock.settimeout(timeout)
            reply = sock.recv(256).decode("utf-8", errors="replace")
            if expect and expect not in reply:
                return {"success": False, "value": None, "error": f"beklenmeyen yanit: {reply!r}"}
            return {"success": True, "value": reply.strip(), "error": None}

    return {"success": True, "value": True, "error": None}


def _collect_log_parser(payload: dict) -> dict:
    file_path = payload["filePath"]
    pattern = payload["pattern"]
    tail_bytes = payload.get("tailBytes", 1048576)

    with open(file_path, "rb") as f:
        f.seek(0, os.SEEK_END)
        size = f.tell()
        f.seek(max(0, size - tail_bytes))
        content = f.read().decode("utf-8", errors="replace")

    matches = re.findall(pattern, content)
    return {"success": True, "value": len(matches), "error": None}


def _collect_custom_command(payload: dict) -> dict:
    """No shell=True, ever - the command is tokenized with shlex and executed
    as a plain argv list. Missing binaries and timeouts are allowed to raise
    (execute_metric_item's outer guard turns them into success:false); a
    non-zero exit is handled explicitly here since subprocess.run() alone
    doesn't raise for that."""
    command = payload["command"]
    timeout = payload.get("timeoutSeconds", REQUEST_TIMEOUT_SECONDS)
    value_type = payload.get("valueType", "raw")
    extract_pattern = payload.get("extractPattern")

    args = shlex.split(command)
    result = subprocess.run(args, capture_output=True, text=True, timeout=timeout)

    if result.returncode != 0:
        error_text = (result.stderr or "komut basarisiz oldu (exit != 0)").strip()
        return {"success": False, "value": None, "error": error_text[:500]}

    output = result.stdout.strip()

    if value_type == "matchCount":
        matches = re.findall(extract_pattern or "", output, re.MULTILINE)
        return {"success": True, "value": len(matches), "error": None}

    if extract_pattern:
        value = _extract_number(output, extract_pattern)
        if value is None:
            return {"success": False, "value": None, "error": "extractPattern eslesmedi"}
        return {"success": True, "value": value, "error": None}

    if value_type == "lineCount":
        lines = [line for line in output.splitlines() if line.strip()]
        return {"success": True, "value": len(lines), "error": None}

    if value_type == "number":
        return {"success": True, "value": _coerce_number(output), "error": None}

    return {"success": True, "value": output, "error": None}


def _predefined_disk_io_and_space(payload: dict) -> dict:
    path = payload.get("path", "/")
    usage = psutil.disk_usage(path)
    value = {
        "diskPercent": usage.percent,
        "diskUsedBytes": usage.used,
        "diskFreeBytes": usage.free,
    }
    io_counters = psutil.disk_io_counters()
    if io_counters is not None:
        value["readBytes"] = io_counters.read_bytes
        value["writeBytes"] = io_counters.write_bytes
    return {"success": True, "value": value, "error": None}


PREDEFINED_FUNCTIONS = {
    "disk_io_and_space": _predefined_disk_io_and_space,
}


def _collect_predefined_template(payload: dict, metric_key: str) -> dict:
    func = PREDEFINED_FUNCTIONS.get(metric_key)
    if func is None:
        return {"success": False, "value": None, "error": f"bilinmeyen predefined metric_key: {metric_key}"}
    return func(payload)


COLLECTORS = {
    "HTTP_ENDPOINT": lambda payload, metric_key: _collect_http_endpoint(payload),
    "PORT_CHECK": lambda payload, metric_key: _collect_port_check(payload),
    "LOG_PARSER": lambda payload, metric_key: _collect_log_parser(payload),
    "CUSTOM_COMMAND": lambda payload, metric_key: _collect_custom_command(payload),
    "PREDEFINED_TEMPLATE": _collect_predefined_template,
}


def execute_metric_item(item: dict) -> dict:
    """Never raises - dispatches item['type'] to a collector and always
    returns a well-formed result dict, even for a malformed commandPayload,
    an unknown type, a missing binary, or any other collector-level failure."""
    metric_key = item.get("metricKey", "")
    try:
        payload = json.loads(item["commandPayload"])
        collector = COLLECTORS.get(item["type"])
        if collector is None:
            result = {"success": False, "value": None, "error": f"bilinmeyen tip: {item.get('type')}"}
        else:
            result = collector(payload, metric_key)
    except Exception as e:
        result = {"success": False, "value": None, "error": str(e)[:500]}

    return {
        "groupItemId": item["groupItemId"],
        "metricKey": metric_key,
        "success": result["success"],
        "value": result["value"],
        "error": result["error"],
    }


def _hash_file(path: str) -> str:
    hasher = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def check_tracked_file(item: dict) -> dict:
    """Never raises - reads+hashes item['filePath'], compares to the
    currentHash the server told it about, and returns exactly one of an
    'unchanged' report, a 'content'+'hash' report (new/changed), or an
    'error' report (FILE_NOT_FOUND/PERMISSION_DENIED). The server, not the
    agent, decides whether new content means a v1 baseline or real drift -
    same "server is the single source of truth" split used for metrics."""
    tracked_file_id = item["trackedFileId"]
    file_path = item["filePath"]
    known_hash = item.get("currentHash")
    try:
        current_hash = _hash_file(file_path)
    except FileNotFoundError:
        return {"trackedFileId": tracked_file_id, "error": "FILE_NOT_FOUND"}
    except PermissionError:
        return {"trackedFileId": tracked_file_id, "error": "PERMISSION_DENIED"}

    if known_hash is not None and current_hash == known_hash:
        return {"trackedFileId": tracked_file_id, "unchanged": True}

    with open(file_path, "r", encoding="utf-8", errors="replace") as f:
        content = f.read()
    return {"trackedFileId": tracked_file_id, "hash": current_hash, "content": content}


def run_config_tracker_cycle(sync_url: str, report_url: str, secret_key: str, hostname: str) -> None:
    try:
        sync_data = fetch_sync(sync_url, secret_key, hostname)
    except requests.exceptions.RequestException as e:
        logger.warning("Yapilandirma takibi sync alinamadi: %s", e)
        return

    due_files = sync_data.get("dueFiles", [])
    if not due_files:
        return

    reports = [check_tracked_file(item) for item in due_files]

    try:
        status_code = push_results(report_url, secret_key, hostname, {"reports": reports})
        logger.info("Yapilandirma takibi raporlari gonderildi (%s dosya) -> HTTP %s", len(reports), status_code)
    except requests.exceptions.RequestException as e:
        logger.warning("Yapilandirma takibi raporlari gonderilemedi: %s", e)


def run_custom_metrics_cycle(sync_url: str, results_url: str, secret_key: str, hostname: str) -> None:
    try:
        sync_data = fetch_sync(sync_url, secret_key, hostname)
    except requests.exceptions.RequestException as e:
        logger.warning("Ozel metrik sync alinamadi: %s", e)
        return

    due_items = sync_data.get("dueItems", [])
    pending_fetch_requests = sync_data.get("pendingFetchRequests", [])

    periodic_results = [execute_metric_item(item) for item in due_items]
    fetch_results = [
        {
            "fetchRequestId": fetch_request["fetchRequestId"],
            "results": [execute_metric_item(item) for item in fetch_request.get("items", [])],
        }
        for fetch_request in pending_fetch_requests
    ]

    if not periodic_results and not fetch_results:
        return

    try:
        status_code = push_results(
            results_url, secret_key, hostname,
            {"periodicResults": periodic_results, "fetchResults": fetch_results},
        )
        logger.info(
            "Ozel metrik sonuclari gonderildi (%s periyodik, %s anlik istek) -> HTTP %s",
            len(periodic_results), len(fetch_results), status_code,
        )
    except requests.exceptions.RequestException as e:
        logger.warning("Ozel metrik sonuclari gonderilemedi: %s", e)


def run():
    configure_logging()
    config = load_config()
    ingest_url = config["ingestUrl"]
    sync_url = config.get("syncUrl")
    results_url = config.get("resultsUrl")
    config_sync_url = config.get("configSyncUrl")
    config_report_url = config.get("configReportUrl")
    secret_key = config["secretKey"]
    hostname = config.get("hostname") or socket.gethostname()

    logger.info("Agent baslatildi. Hedef: %s | Hostname: %s", ingest_url, hostname)

    consecutive_forbidden = 0
    loop_count = 0

    while True:
        try:
            payload = collect_metrics(hostname)
            status_code = send_metrics(ingest_url, secret_key, hostname, payload)

            if status_code == 200:
                consecutive_forbidden = 0
                logger.info(
                    "Gonderildi -> CPU: %%%s RAM: %%%s Disk: %%%s",
                    payload["cpuPercent"], payload["ramPercent"], payload["diskPercent"],
                )
            else:
                consecutive_forbidden, should_terminate = process_response(status_code, consecutive_forbidden)

                if status_code == 403:
                    logger.warning(
                        "403 Forbidden alindi (%s/%s) - secret key iptal edilmis olabilir",
                        consecutive_forbidden, MAX_CONSECUTIVE_FORBIDDEN,
                    )
                    if should_terminate:
                        logger.error("Secret key iptal edildi, agent sonlandiriliyor (self-destruct)")
                        sys.exit(0)
                else:
                    logger.warning("Beklenmeyen durum kodu: %s", status_code)

            loop_count += 1
            if sync_url and results_url and loop_count % SYNC_EVERY_N_LOOPS == 0:
                run_custom_metrics_cycle(sync_url, results_url, secret_key, hostname)
            if config_sync_url and config_report_url and loop_count % SYNC_EVERY_N_LOOPS == 0:
                run_config_tracker_cycle(config_sync_url, config_report_url, secret_key, hostname)

        except requests.exceptions.RequestException as e:
            logger.error("Gonderim hatasi: %s", e)
        except FileNotFoundError:
            logger.error("Konfigurasyon dosyasi bulunamadi: %s", CONFIG_PATH)
            sys.exit(1)

        time.sleep(SEND_INTERVAL_SECONDS)


if __name__ == "__main__":
    run()
