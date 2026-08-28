<p align="center">
  <img src="https://img.shields.io/badge/Monitoring-Air--Gapped-FF4B4B?style=for-the-badge&logo=serverfault&logoColor=white" alt="Air-Gapped" />
  <img src="https://img.shields.io/badge/Status-Internal%20PoC-yellow?style=for-the-badge" alt="Status" />
  <img src="https://img.shields.io/badge/License-Internal-blue?style=for-the-badge" alt="License" />
</p>

<h1 align="center">🛰️ Turkcell Server Monitoring Project</h1>

<p align="center">
  <strong>Disaster-Recovery Grade Monitoring & Config-Drift Detection for Air-Gapped Linux Infrastructure</strong>
  <br />
  <i>Zero-internet metric collection • SHA-256 config drift detection • Fully offline AI anomaly analysis</i>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Spring_Boot-3.3.2-6DB33F?style=flat-square&logo=spring-boot&logoColor=white" />
  <img src="https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white" />
  <img src="https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat-square&logo=postgresql&logoColor=white" />
  <img src="https://img.shields.io/badge/Python-Agent-3776AB?style=flat-square&logo=python&logoColor=white" />
  <img src="https://img.shields.io/badge/Ollama-Offline_LLM-000000?style=flat-square&logo=ollama&logoColor=white" />
  <img src="https://img.shields.io/badge/Docker-Ready-2496ED?style=flat-square&logo=docker&logoColor=white" />
  <img src="https://img.shields.io/badge/Flyway-Migrations-CC0200?style=flat-square&logo=flyway&logoColor=white" />
</p>

---

## 🎯 Project Overview

**Turkcell Server Monitoring Project** is a disaster-recovery and analytics platform built to monitor Linux servers inside a fully **air-gapped** (internet-isolated) corporate network. It continuously tracks host-level resource metrics, detects unauthorized changes to critical configuration files, and runs anomaly detection through a **locally-hosted LLM** — with no component ever requiring an outbound internet connection.

Built for environments where every dependency, container image, and AI model must be brought in offline and every byte of agent-to-backend traffic must be cryptographically authenticated.

---

## ✨ Key Features

### 📊 Infrastructure Monitoring
- **Lightweight Python agent** — outbound-only, no listening port, no stored SSH credentials
- **Live CPU / RAM / Disk metrics** pushed every 5 seconds per host
- **Custom metric framework** — HTTP endpoint checks, TCP/port probes, log-pattern counters, arbitrary shell commands
- **On-demand "Fetch Now"** execution delivered over the same outbound heartbeat channel

### 🛡️ Configuration Drift Detection
- **SHA-256 fingerprinting** of every tracked configuration file
- **Full version history** with line-level diffs between any two captured versions
- **Server-side source of truth** — drift stays flagged until a human explicitly reviews and accepts the new baseline
- **Instant email + in-app alerts** to admins and the file's original creator on any detected drift

### 🤖 Offline AI Ops (AIOps)
- **Ollama-powered** local LLM — zero external API calls, zero data leaving the network
- **Multi-metric correlation analysis** across CPU/RAM/disk to catch abnormal patterns
- **Root-cause analysis (RCA)** over recent failed custom-metric executions
- **Critical-threshold watchdog** with AI-generated (or template-fallback) alert summaries
- **Daily system health digest**, delivered by email every morning at 08:00
- **Embedded chat assistant** scoped to the metric groups an operator chooses to track

### 🔐 Security & Provisioning
- **HMAC-SHA256** request signing with constant-time verification and anti-replay windows
- **AES-256-GCM** encryption of per-server secrets at rest
- **Zero-persistence SSH provisioning** — root credentials are used once, in memory, and never stored
- **Self-destructing agents** — three consecutive `403` responses (revoked credentials) and the agent terminates itself
- **JWT authentication** with optional email-based 2FA

---

## 🏗️ Architecture

```
┌───────────────────────┐    one-way, HMAC-signed HTTP    ┌───────────────────────────┐
│    Linux Target Host   │ ───────────────────────────────▶│   Backend / Monitoring     │
│   (Monitoring Agent)    │                                 │   Service (Spring Boot)    │
│  outbound-only, no      │◀──── polls for work ─────────── │                             │
│  listening port         │      (fetch_sync)               └──────────────┬──────────────┘
└───────────────────────┘                                                  │
                                                        fan-out, executed in parallel
                                    ┌────────────────────────────┼────────────────────────────┐
                                    ▼                             ▼                             ▼
                       ┌─────────────────────┐       ┌─────────────────────┐       ┌─────────────────────┐
                       │      PostgreSQL       │       │     AIOps Module      │       │  Notification System  │
                       │      (Database)        │       │  (Ollama, local LLM)   │       │   (Email / in-app)      │
                       └─────────────────────┘       └─────────────────────┘       └─────────────────────┘
```

Every inbound agent request results in **one synchronous, authenticated write** to PostgreSQL, followed by a **non-blocking, parallel fan-out** to the AIOps scheduler (`@Scheduled` / `@Async`) and the notification pipeline. A slow AI job or an unreachable SMTP relay can never create backpressure on metric ingestion from hundreds of agents.

> 📄 For the full technical breakdown of HMAC verification, SHA-256 drift detection, and the offline AI pipeline, see [**Security & Integrity**](#-security--integrity) and [**Offline AI (AIOps)**](#-offline-ai-aiops) below.

---

## 🔒 Security & Integrity

### SHA-256 Configuration Hashing

Every tracked file is fingerprinted with SHA-256 on the agent side (`_hash_file()` in `agent.py`). The **server is the sole authority** on what a hash change means:

| Report | Meaning | Resulting Status |
|:-------|:--------|:------------------|
| Hash unchanged | No drift | `IN_SYNC` |
| First-ever report for a file | New baseline captured | `IN_SYNC` (v1) |
| Hash changed vs. known baseline | **Unauthorized drift** | `DRIFT_DETECTED` — stays flagged until an admin accepts the new baseline |
| File unreadable | Access problem | `FILE_NOT_FOUND` / `PERMISSION_DENIED` |

### HMAC-SHA256 Request Authentication

```
signature = hex( HMAC-SHA256( per_server_secret_key, timestamp + raw_request_body ) )
```

1. Every agent request carries `X-Timestamp`, `X-Signature`, and `X-Server-Hostname`.
2. The backend decrypts the target server's AES-256-GCM-encrypted secret, recomputes the signature, and compares it in **constant time** (`MessageDigest.isEqual`) to prevent timing attacks.
3. A configurable **anti-replay window** (`HMAC_WINDOW_SECONDS`, default 300s) rejects stale requests.
4. A `REVOKED` server yields `403 Forbidden` on every call — the agent treats **three consecutive 403s** as a revoked-credential signal and self-terminates.

### Zero-Persistence Provisioning

Adding a server uses a single, one-time root SSH session to create a locked-down `monitoring_user` account and deploy the agent. The root password is used **only in memory**, wiped immediately after the session closes, and **never logged or stored**.

---

## 🧠 Offline AI (AIOps)

Despite running in a network with **no internet access**, the platform integrates [**Ollama**](https://ollama.com) — a fully local LLM runtime — to power anomaly detection and log analysis. No external API, no cloud model, no data ever leaves the corporate network.

| Job | Cadence | Purpose |
|:----|:--------|:--------|
| **Multi-metric correlation** | Every 5 min (configurable) | Flags abnormal CPU/RAM/disk relationships across tracked hosts |
| **Root-cause analysis (RCA)** | Every 5 min | Analyzes recent failed custom-metric runs for a root-cause hypothesis |
| **Critical-threshold watchdog** | Every 5 min | Compares live readings to configurable thresholds (default 90%); falls back to a template message if Ollama is unreachable |
| **Daily health summary** | 08:00 daily (cron) | Narrative summary of the last 24h of metrics and failures |

Default model: `qwen2.5-coder:1.5b`, served from `http://127.0.0.1:11434` — fully configurable via `OLLAMA_BASE_URL` / `OLLAMA_MODEL`. Every failure mode (timeout, connection refused, empty response) degrades gracefully — core monitoring and drift detection never depend on the AI layer being up.

---

## 🏛️ Tech Stack

| Layer | Technology | Purpose |
|:------|:-----------|:--------|
| **Backend** | Java 21, Spring Boot 3.3.2 | REST API — Web, Security, Data JPA, Validation, Mail |
| **Database** | PostgreSQL 16 | Persistence, versioned with **Flyway** migrations |
| **Auth** | JWT (`jjwt` 0.12.6), BCrypt | Stateless authentication, optional email 2FA |
| **Agent transport security** | HMAC-SHA256, AES-256-GCM | Request signing + secrets-at-rest encryption |
| **Provisioning** | JSch (SSH) | Zero-persistence remote agent bootstrap |
| **Monitoring Agent** | Python 3 (`psutil`, `requests`) | Outbound-only host metric/config collector |
| **Offline AI** | Ollama (local LLM runtime) | Anomaly detection, RCA, chat assistant |
| **Frontend** | HTML / CSS / vanilla JS | Served embedded from Spring Boot or a standalone Node dev server |
| **Build / Packaging** | Maven, multi-stage Docker | `maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre` |
| **Testing** | JUnit 5, Spring Security Test, Testcontainers | Integration tests against a real PostgreSQL container |

---

## 🚀 Quick Start

### 🐳 Docker (air-gapped pattern)

The target network has **no internet access**, so images are built/pulled on a connected machine and transferred offline.

**On a machine with internet access:**
```bash
# Build the application image
docker compose build

# Export every image the stack needs into one tarball
docker save -o monitoring-poc-images.tar monitoring-poc-app postgres:16-alpine
```

**Transfer `monitoring-poc-images.tar` into the isolated network, then on the target host:**
```bash
# Load the images - no registry pull required
docker load -i monitoring-poc-images.tar

# 1. Copy the environment template and fill in real values
cp .env.example .env

# 2. Bring the stack up from the pre-loaded images
docker compose up -d
```

| Service | URL |
|:--------|:----|
| Backend + embedded frontend | `http://localhost:8080` |
| PostgreSQL | `localhost:5432` |

Ollama must be installed and its model pulled (`ollama pull qwen2.5-coder:1.5b`) on a connected machine beforehand and transferred the same way; the backend only needs a reachable Ollama HTTP endpoint at runtime.

<details>
<summary><b>⚙️ Bare-metal / systemd (no Docker)</b></summary>

```bash
sudo mkdir -p /opt/monitoring-poc /etc/monitoring-poc
sudo cp target/monitoring-poc.jar /opt/monitoring-poc/monitoring-poc.jar
sudo cp scripts/monitoring-poc.env.example /etc/monitoring-poc/monitoring-poc.env
sudo chmod 600 /etc/monitoring-poc/monitoring-poc.env   # holds secrets
# edit /etc/monitoring-poc/monitoring-poc.env with real values
sudo cp scripts/monitoring-poc.service /etc/systemd/system/monitoring-poc.service
sudo systemctl daemon-reload
sudo systemctl enable --now monitoring-poc
```

Run `scripts/bootstrap-target-host.sh` once on each **new monitored host** first (installs `python3`, `pip3`, `cron`) — then add the host from the dashboard's "Sunucu Ekle" flow, which provisions the agent over SSH automatically.

</details>

---

## 🔑 Environment Variables

Copy `.env.example` to `.env` and fill in real values — **never deploy with the example defaults**:

```env
# PostgreSQL
DB_PASSWORD=CHANGE_ME

# 256-bit JWT signing secret - generate: openssl rand -hex 32
JWT_SECRET=CHANGE_ME_min_32_bytes_random_value

# Passphrase deriving the AES-256 key that encrypts per-server HMAC secrets
MASTER_KEY=CHANGE_ME_random_passphrase_for_aes_key_derivation

# Bootstrap admin, seeded once on first boot if no ADMIN exists yet
BOOTSTRAP_ADMIN_USERNAME=admin
BOOTSTRAP_ADMIN_EMAIL=admin@monitoring.local
BOOTSTRAP_ADMIN_PASSWORD=CHANGE_ME

# THIS backend's address as reachable FROM the monitored hosts' network
# (localhost/127.0.0.1 will NOT work - agents run on remote targets)
APP_INGEST_BASE_URL=http://YOUR_SERVER_IP:8080

# Offline AI
OLLAMA_BASE_URL=http://127.0.0.1:11434
OLLAMA_MODEL=qwen2.5-coder:1.5b

# SMTP relay + drift/alert email delivery
SMTP_HOST=smtp.example.com
MAIL_ENABLED=true
```

See `.env.example` for the complete list, including HMAC replay window, 2FA toggle, "Fetch Now" poll tuning, and AIOps thresholds.

⚠️ **Never commit `.env`** — it is git-ignored by design and should hold this environment's real secrets and internal addresses only.

---

## 📂 Project Structure

```
Turkcell_Server_Monitoring_Project/
├── 🐳 Dockerfile, docker-compose.yml   # Multi-stage build & container orchestration
├── 📄 .env.example                     # Environment variable template
├── 🐍 agent.py                         # Reference copy of the monitoring agent
├── 📂 scripts/                         # Provisioning & systemd deployment scripts
│   ├── bootstrap-target-host.sh
│   ├── monitoring-poc.service
│   └── monitoring-poc.env.example
├── 📂 src/main/java/com/monitoring/poc/
│   ├── auth/                # JWT login, register, 2FA
│   ├── security/            # HmacAuthFilter, CryptoUtil, JwtService, SecurityConfig
│   ├── servers/              # SSH provisioning (JSch), server lifecycle
│   ├── metrics/               # Custom metric groups, agent sync protocol
│   ├── configs/                # Config Tracker - drift detection, diff engine
│   ├── aiops/                    # Ollama client, AIOps scheduler & service
│   ├── notifications/             # In-app notification pipeline
│   ├── email/                      # Async SMTP dispatch
│   ├── admin/, profile/             # User & role management
│   └── entity/, repository/, enums/  # JPA model layer
├── 📂 src/main/resources/
│   ├── db/migration/          # Flyway SQL migrations (V1 → V16)
│   ├── agent-template/         # agent.py template uploaded during provisioning
│   └── static/                  # Embedded frontend (served by Spring Boot)
├── 📂 frontend/                # Standalone frontend + Node dev server (server.js)
└── 📂 tests/                   # Python agent test suite (pytest)
```

---

## 🔌 API Reference

All endpoints below are prefixed `/api`. Agent-facing endpoints (`/agent/**`, legacy `POST /metrics`) are HMAC-authenticated instead of JWT; everything else requires a Bearer token except `/auth/**`.

<details>
<summary><b>🔑 Auth & Profile</b></summary>

| Method | Endpoint | Description |
|:-------|:---------|:-------------|
| `POST` | `/auth/register` | Register a new user |
| `POST` | `/auth/login` | Login, issues JWT (or triggers 2FA) |
| `POST` | `/auth/verify-2fa` | Verify OTP code |
| `GET` | `/profile/me` | Current user profile |
| `POST` | `/profile/change-requests/email` | Request an email change (admin-approved) |

</details>

<details>
<summary><b>🖥️ Servers & Provisioning</b></summary>

| Method | Endpoint | Description |
|:-------|:---------|:-------------|
| `GET` | `/servers` | List registered servers |
| `POST` | `/servers/provision` | Zero-persistence SSH provisioning of a new host |
| `DELETE` | `/servers/{id}` | Revoke a server (agent self-terminates) |

</details>

<details>
<summary><b>📈 Metrics & Custom Metric Groups</b></summary>

| Method | Endpoint | Description |
|:-------|:---------|:-------------|
| `POST` | `/metrics` | HMAC-signed CPU/RAM/disk ingestion (agent → backend) |
| `GET` | `/metrics/{hostname}/history` | Recent metric history for a host |
| `GET` / `POST` | `/metric-groups` | List / create custom metric groups |
| `POST` | `/metric-groups/{id}/fetch-now` | On-demand execution via the agent heartbeat channel |
| `GET` | `/agent/metrics/sync` | Agent polls for due work (HMAC) |
| `POST` | `/agent/metrics/results` | Agent reports execution results (HMAC) |

</details>

<details>
<summary><b>🛡️ Config Tracker</b></summary>

| Method | Endpoint | Description |
|:-------|:---------|:-------------|
| `POST` | `/configs/tracked-files` | Register a file for drift tracking |
| `GET` | `/configs/tracked-files/{id}/history` | Version history |
| `GET` | `/configs/tracked-files/{id}/diff` | Line-level diff between two versions |
| `POST` | `/configs/tracked-files/{id}/accept-baseline` | Acknowledge & clear a detected drift |
| `GET` | `/agent/configs/sync` | Agent polls for due file checks (HMAC) |

</details>

<details>
<summary><b>🤖 AIOps</b></summary>

| Method | Endpoint | Description |
|:-------|:---------|:-------------|
| `POST` | `/v1/aiops/chat` | Ask the embedded AI assistant a question |
| `GET` | `/v1/aiops/anomalies` | Recent AI-detected anomaly cards |
| `POST` | `/v1/aiops/config` | Configure tracked groups, alert email, daily summary |

</details>

---

## 🚨 Alerting & Use Cases

Alerts are raised as `AiOpsAnomaly` cards and/or delivered by email, based on conditions defined directly in the codebase:

- **Critical resource threshold breach** — CPU, RAM, or disk usage on any tracked host meets or exceeds its configured threshold (default **90%**)
- **Configuration drift (hash mismatch)** — a tracked file's live SHA-256 hash no longer matches its known baseline
- **Configuration read failure** — a tracked file becomes unreadable (`FILE_NOT_FOUND` / `PERMISSION_DENIED`)
- **Anomalous multi-metric correlation** — AI-detected abnormal relationship between CPU/RAM/disk on a host
- **Custom metric / log-based failures** — an `HTTP_ENDPOINT`, `PORT_CHECK`, `LOG_PARSER`, or `CUSTOM_COMMAND` metric fails
- **Revoked agent self-destruct** — three consecutive `403 Forbidden` responses terminate the agent
- **Workflow notifications** — metric requests, approvals/rejections, and profile change requests
- **Daily system health digest** — scheduled 08:00 narrative summary, independent of any threshold breach

---

## 📝 Repository Notes

- `.env` is git-ignored and holds this environment's real secrets/addresses — only `.env.example` (placeholders) is versioned.
- All secret-shaped defaults in `application.properties` were replaced with non-functional placeholders; real values are injected exclusively via environment variables at deploy time.
- `react-components/` (an unfinished, unwired React experiment) is intentionally excluded from version control.

---

## 📞 Contact

**Developer:** Muhammet Emir Kılınç
**Email:** emirkilinc27@gmail.com
**GitHub:** [@EmirrKilinc](https://github.com/EmirrKilinc)

---

<p align="center">
  <br />
  <i>🛰️ Monitoring the fleet, offline by design.</i>
  <br /><br />
  <strong>© 2026 Turkcell Server Monitoring Project</strong>
</p>
