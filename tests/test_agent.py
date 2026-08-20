import hashlib
import hmac
import json
import sys
from pathlib import Path
from unittest.mock import MagicMock, patch

import requests

import agent


def test_sign_payload_matches_independently_computed_hmac():
    secret = "my-secret"
    timestamp = 1700000000
    body = '{"hostname":"h1","cpuPercent":1.0,"ramPercent":2.0,"diskPercent":3.0}'

    expected = hmac.new(
        secret.encode("utf-8"), f"{timestamp}{body}".encode("utf-8"), hashlib.sha256
    ).hexdigest()

    assert agent.sign_payload(secret, timestamp, body) == expected


def test_sign_payload_changes_when_body_changes():
    sig1 = agent.sign_payload("secret", 1700000000, '{"a":1}')
    sig2 = agent.sign_payload("secret", 1700000000, '{"a":2}')
    assert sig1 != sig2


def test_process_response_ignores_success_status():
    new_count, should_terminate = agent.process_response(200, 2)
    assert new_count == 0
    assert should_terminate is False


def test_process_response_counts_consecutive_403s_and_terminates_on_third():
    count = 0
    should_terminate = False

    for _ in range(agent.MAX_CONSECUTIVE_FORBIDDEN):
        count, should_terminate = agent.process_response(403, count)

    assert count == agent.MAX_CONSECUTIVE_FORBIDDEN
    assert should_terminate is True


def test_process_response_resets_on_non_forbidden_status():
    count, _ = agent.process_response(403, 0)
    assert count == 1

    count, should_terminate = agent.process_response(500, count)
    assert count == 0
    assert should_terminate is False


@patch("agent.requests.post")
def test_send_metrics_sends_correct_hmac_headers(mock_post):
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_post.return_value = mock_response

    payload = {"hostname": "h1", "cpuPercent": 10.0, "ramPercent": 20.0, "diskPercent": 30.0}
    status_code = agent.send_metrics("http://example.local/api/metrics", "sekret", "h1", payload)

    assert status_code == 200
    call_kwargs = mock_post.call_args.kwargs

    sent_body = call_kwargs["data"]
    headers = call_kwargs["headers"]
    assert headers["X-Server-Hostname"] == "h1"

    expected_signature = hmac.new(
        "sekret".encode("utf-8"),
        f"{headers['X-Timestamp']}{sent_body}".encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()
    assert headers["X-Signature"] == expected_signature
    assert json.loads(sent_body) == payload


def test_load_config_reads_json_file(tmp_path):
    config_file = tmp_path / "agent_config.json"
    config_file.write_text(
        json.dumps({"secretKey": "abc", "ingestUrl": "http://x/api/metrics", "hostname": "h1"}),
        encoding="utf-8",
    )

    config = agent.load_config(str(config_file))

    assert config["secretKey"] == "abc"
    assert config["hostname"] == "h1"


@patch("agent.requests.get")
def test_fetch_sync_signs_get_request_with_empty_body(mock_get):
    mock_response = MagicMock()
    mock_response.json.return_value = {"dueItems": [], "pendingFetchRequests": []}
    mock_get.return_value = mock_response

    result = agent.fetch_sync("http://example.local/api/agent/metrics/sync", "sekret", "h1")

    assert result == {"dueItems": [], "pendingFetchRequests": []}
    headers = mock_get.call_args.kwargs["headers"]
    expected_signature = hmac.new(
        "sekret".encode("utf-8"), f"{headers['X-Timestamp']}".encode("utf-8"), hashlib.sha256
    ).hexdigest()
    assert headers["X-Signature"] == expected_signature


# ---------- HTTP_ENDPOINT collector ----------

@patch("agent.requests.request")
def test_http_endpoint_collector_extracts_number_via_pattern(mock_request):
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.text = "Active connections: 42 \nsomething else"
    mock_request.return_value = mock_response

    result = agent._collect_http_endpoint({
        "url": "http://127.0.0.1/nginx_status",
        "expectedStatus": 200,
        "extractPattern": r"Active connections:\s*(\d+)",
        "valueType": "number",
    })

    assert result["success"] is True
    assert result["value"] == 42


@patch("agent.requests.request")
def test_http_endpoint_collector_fails_on_unexpected_status(mock_request):
    mock_response = MagicMock()
    mock_response.status_code = 500
    mock_request.return_value = mock_response

    result = agent._collect_http_endpoint({"url": "http://127.0.0.1/health", "expectedStatus": 200, "valueType": "bool"})

    assert result["success"] is False
    assert result["error"] is not None


# ---------- PORT_CHECK collector ----------

@patch("agent.socket.create_connection")
def test_port_check_collector_succeeds_on_connect(mock_create_connection):
    mock_sock = MagicMock()
    mock_create_connection.return_value.__enter__.return_value = mock_sock

    result = agent._collect_port_check({"host": "127.0.0.1", "port": 6379})

    assert result["success"] is True
    assert result["value"] is True


@patch("agent.socket.create_connection", side_effect=OSError("connection refused"))
def test_port_check_collector_fails_cleanly_when_unreachable(mock_create_connection):
    item = {"groupItemId": 1, "metricKey": "redis_port", "type": "PORT_CHECK",
            "commandPayload": json.dumps({"host": "127.0.0.1", "port": 6379})}

    result = agent.execute_metric_item(item)

    assert result["success"] is False
    assert result["error"] is not None


# ---------- LOG_PARSER collector ----------

def test_log_parser_collector_counts_pattern_matches(tmp_path):
    log_file = tmp_path / "app.log"
    log_file.write_text("INFO started\nERROR boom\nINFO ok\nERROR boom again\n", encoding="utf-8")

    result = agent._collect_log_parser({"filePath": str(log_file), "pattern": "ERROR", "tailBytes": 1048576})

    assert result["success"] is True
    assert result["value"] == 2


# ---------- CUSTOM_COMMAND collector ----------

# shlex.split() runs in POSIX mode (correct for the Linux target hosts this
# actually deploys to), which treats backslashes as escape characters - on
# this Windows dev box sys.executable contains backslashes, so it must be
# forward-slash-normalized before being embedded in a test command string,
# or shlex would mangle the path itself.
_PYTHON_FOR_SHLEX = sys.executable.replace("\\", "/")


def test_custom_command_collector_parses_numeric_output():
    command = f'{_PYTHON_FOR_SHLEX} -c "print(42)"'

    result = agent._collect_custom_command({"command": command, "valueType": "number"})

    assert result["success"] is True
    assert result["value"] == 42


def test_custom_command_collector_reports_non_zero_exit_without_raising():
    command = f'{_PYTHON_FOR_SHLEX} -c "import sys; sys.exit(1)"'

    result = agent._collect_custom_command({"command": command})

    assert result["success"] is False
    assert result["error"] is not None


def test_execute_metric_item_never_raises_on_missing_binary():
    item = {
        "groupItemId": 5,
        "metricKey": "missing_binary_test",
        "type": "CUSTOM_COMMAND",
        "commandPayload": json.dumps({"command": "this-binary-should-not-exist-xyz123 --version"}),
    }

    result = agent.execute_metric_item(item)

    assert result["success"] is False
    assert result["groupItemId"] == 5
    assert result["error"] is not None


def test_execute_metric_item_never_raises_on_malformed_payload():
    item = {"groupItemId": 6, "metricKey": "bad_payload", "type": "HTTP_ENDPOINT", "commandPayload": "not-json{{{"}

    result = agent.execute_metric_item(item)

    assert result["success"] is False
    assert result["error"] is not None


def test_execute_metric_item_reports_unknown_type_without_raising():
    item = {"groupItemId": 7, "metricKey": "unknown_type", "type": "SOMETHING_NEW", "commandPayload": "{}"}

    result = agent.execute_metric_item(item)

    assert result["success"] is False


def test_custom_command_collector_match_count_counts_matching_lines():
    command = f'{_PYTHON_FOR_SHLEX} -c "print(chr(10).join([\'Z+\', \'S\', \'Z\', \'R\']))"'

    result = agent._collect_custom_command({"command": command, "extractPattern": "^Z", "valueType": "matchCount"})

    assert result["success"] is True
    assert result["value"] == 2


def test_custom_command_collector_match_count_is_zero_not_an_error_when_nothing_matches():
    command = f'{_PYTHON_FOR_SHLEX} -c "print(chr(10).join([\'S\', \'R\']))"'

    result = agent._collect_custom_command({"command": command, "extractPattern": "^Z", "valueType": "matchCount"})

    assert result["success"] is True
    assert result["value"] == 0


# ---------- PREDEFINED_TEMPLATE collector ----------

@patch("agent.psutil.disk_io_counters")
@patch("agent.psutil.disk_usage")
def test_predefined_disk_io_and_space_uses_psutil(mock_disk_usage, mock_disk_io_counters):
    mock_disk_usage.return_value = MagicMock(percent=55.5, used=1000, free=2000)
    mock_disk_io_counters.return_value = MagicMock(read_bytes=10, write_bytes=20)

    item = {
        "groupItemId": 8,
        "metricKey": "disk_io_and_space",
        "type": "PREDEFINED_TEMPLATE",
        "commandPayload": json.dumps({"path": "/"}),
    }

    result = agent.execute_metric_item(item)

    assert result["success"] is True
    assert result["value"]["diskPercent"] == 55.5
    assert result["value"]["readBytes"] == 10


def test_predefined_template_reports_unknown_metric_key():
    item = {"groupItemId": 9, "metricKey": "totally_unknown", "type": "PREDEFINED_TEMPLATE", "commandPayload": "{}"}

    result = agent.execute_metric_item(item)

    assert result["success"] is False


# ---------- run_custom_metrics_cycle ----------

@patch("agent.push_results")
@patch("agent.fetch_sync")
def test_run_custom_metrics_cycle_pushes_periodic_and_fetch_results(mock_fetch_sync, mock_push_results):
    due_item = {"groupItemId": 1, "groupId": 10, "metricDefinitionId": 100, "metricKey": "m1",
                "type": "PREDEFINED_TEMPLATE", "commandPayload": "{}"}
    mock_fetch_sync.return_value = {
        "dueItems": [due_item],
        "pendingFetchRequests": [{"fetchRequestId": 55, "groupId": 10, "items": [due_item]}],
    }
    mock_push_results.return_value = 200

    with patch("agent.execute_metric_item", return_value={"groupItemId": 1, "metricKey": "m1", "success": True, "value": 1, "error": None}):
        agent.run_custom_metrics_cycle("http://x/sync", "http://x/results", "sekret", "h1")

    assert mock_push_results.called
    pushed_payload = mock_push_results.call_args.args[3]
    assert len(pushed_payload["periodicResults"]) == 1
    assert len(pushed_payload["fetchResults"]) == 1
    assert pushed_payload["fetchResults"][0]["fetchRequestId"] == 55


@patch("agent.push_results")
@patch("agent.fetch_sync")
def test_run_custom_metrics_cycle_skips_push_when_nothing_to_report(mock_fetch_sync, mock_push_results):
    mock_fetch_sync.return_value = {"dueItems": [], "pendingFetchRequests": []}

    agent.run_custom_metrics_cycle("http://x/sync", "http://x/results", "sekret", "h1")

    mock_push_results.assert_not_called()


@patch("agent.fetch_sync", side_effect=requests.exceptions.RequestException("network down"))
def test_run_custom_metrics_cycle_never_raises_when_sync_fails(mock_fetch_sync):
    # Must not raise - a sync failure should never take down the main heartbeat loop.
    agent.run_custom_metrics_cycle("http://x/sync", "http://x/results", "sekret", "h1")


# ---------- Configuration drift tracker ----------

def test_check_tracked_file_reports_unchanged_when_hash_matches(tmp_path):
    config_file = tmp_path / "app.conf"
    config_file.write_text("setting=1\n", encoding="utf-8")
    known_hash = agent._hash_file(str(config_file))

    result = agent.check_tracked_file({"trackedFileId": 1, "filePath": str(config_file), "currentHash": known_hash})

    assert result == {"trackedFileId": 1, "unchanged": True}


def test_check_tracked_file_reports_content_and_hash_when_changed(tmp_path):
    config_file = tmp_path / "app.conf"
    config_file.write_text("setting=2\n", encoding="utf-8")

    result = agent.check_tracked_file({"trackedFileId": 1, "filePath": str(config_file), "currentHash": "stale-hash"})

    assert result["trackedFileId"] == 1
    assert result["content"] == "setting=2\n"
    assert result["hash"] == agent._hash_file(str(config_file))


def test_check_tracked_file_reports_content_on_first_capture_with_no_known_hash(tmp_path):
    config_file = tmp_path / "app.conf"
    config_file.write_text("setting=3\n", encoding="utf-8")

    result = agent.check_tracked_file({"trackedFileId": 1, "filePath": str(config_file), "currentHash": None})

    assert result["content"] == "setting=3\n"
    assert "unchanged" not in result


def test_check_tracked_file_reports_file_not_found():
    result = agent.check_tracked_file({"trackedFileId": 1, "filePath": "/nonexistent/path/app.conf", "currentHash": None})

    assert result == {"trackedFileId": 1, "error": "FILE_NOT_FOUND"}


@patch("agent._hash_file", side_effect=PermissionError("denied"))
def test_check_tracked_file_reports_permission_denied(mock_hash_file):
    result = agent.check_tracked_file({"trackedFileId": 1, "filePath": "/some/restricted/app.conf", "currentHash": None})

    assert result == {"trackedFileId": 1, "error": "PERMISSION_DENIED"}


@patch("agent.push_results")
@patch("agent.fetch_sync")
def test_run_config_tracker_cycle_pushes_reports_for_due_files(mock_fetch_sync, mock_push_results):
    mock_fetch_sync.return_value = {"dueFiles": [{"trackedFileId": 1, "filePath": "/x/app.conf", "currentHash": None}]}
    mock_push_results.return_value = 200

    with patch("agent.check_tracked_file", return_value={"trackedFileId": 1, "unchanged": True}):
        agent.run_config_tracker_cycle("http://x/configs/sync", "http://x/configs/report", "sekret", "h1")

    assert mock_push_results.called
    pushed_payload = mock_push_results.call_args.args[3]
    assert pushed_payload["reports"] == [{"trackedFileId": 1, "unchanged": True}]


@patch("agent.push_results")
@patch("agent.fetch_sync")
def test_run_config_tracker_cycle_skips_push_when_nothing_due(mock_fetch_sync, mock_push_results):
    mock_fetch_sync.return_value = {"dueFiles": []}

    agent.run_config_tracker_cycle("http://x/configs/sync", "http://x/configs/report", "sekret", "h1")

    mock_push_results.assert_not_called()


@patch("agent.fetch_sync", side_effect=requests.exceptions.RequestException("network down"))
def test_run_config_tracker_cycle_never_raises_when_sync_fails(mock_fetch_sync):
    agent.run_config_tracker_cycle("http://x/configs/sync", "http://x/configs/report", "sekret", "h1")


# ---------- Deployment safety net ----------

def test_template_copy_matches_root_agent():
    """The template uploaded to target hosts (SshProvisioningService) and the
    root dev copy imported by these tests must never drift apart."""
    repo_root = Path(__file__).resolve().parent.parent
    root_agent = repo_root / "agent.py"
    template_agent = repo_root / "src" / "main" / "resources" / "agent-template" / "agent.py"

    assert root_agent.read_text(encoding="utf-8") == template_agent.read_text(encoding="utf-8")
