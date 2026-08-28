# Turkcell Server Monitoring Project

An enterprise disaster-recovery and analytics platform for monitoring Linux servers inside a fully **air-gapped** (internet-isolated) network. The system continuously collects host-level metrics, detects unauthorized changes in tracked configuration files, and uses an entirely offline AI model to surface anomalies — all without a single outbound connection to the public internet.

---

## Table of Contents

1. [Purpose and Scope](#1-purpose-and-scope)
2. [System Architecture](#2-system-architecture)
3. [Security and Integrity Guarantees](#3-security-and-integrity-guarantees)
4. [Offline AI Integration (AIOps)](#4-offline-ai-integration-aiops)
5. [Technology Stack](#5-technology-stack)
6. [Deployment](#6-deployment)
7. [Use Cases and Alerting Triggers](#7-use-cases-and-alerting-triggers)
8. [Repository Notes](#8-repository-notes)

---

## 1. Purpose and Scope

This project was designed for a closed-circuit (air-gapped) corporate network where servers cannot reach — and cannot be reached from — the public internet. Within that constraint, it provides two core capabilities:

- **Infrastructure monitoring**: continuous collection of CPU, RAM, and disk utilization from every registered Linux host, plus a fully customizable custom-metric framework (HTTP checks, TCP/port probes, log-pattern counters, and arbitrary shell commands) for host- or application-specific signals.
- **Configuration drift detection**: cryptographic tracking of critical configuration files so that any unauthorized or unexpected modification is detected, versioned, and escalated — a disaster-recovery and compliance capability as much as an observability one.

Because no component of the system depends on internet access — including its AI layer — it is suitable for classified, regulated, or otherwise network-isolated enterprise environments.

## 2. System Architecture

```
 ┌────────────────────┐        one-way HMAC-signed HTTPS/HTTP        ┌───────────────────────────┐
 │  Linux Target Host  │ ─────────────────────────────────────────▶  │   Backend / Monitoring     │
 │  (Monitoring Agent) │                                              │   Service (Spring Boot)    │
 │                      │ ◀───── polls for work (fetch_sync) ───────  │                             │
 └────────────────────┘                                              └─────────────┬──────────────┘
                                                                                     │
                                                          fan-out, executed in parallel
                                                     ┌───────────────┬───────────────┴───────────────┐
                                                     ▼               ▼                                ▼
                                          ┌─────────────────┐ ┌─────────────┐             ┌─────────────────────┐
                                          │   PostgreSQL     │ │ AI Module   │             │ Notification System  │
                                          │   (Database)     │ │ (Ollama LLM)│             │ (Email / In-app)      │
                                          └─────────────────┘ └─────────────┘             └─────────────────────┘
```

### Monitoring Agent (`agent.py`)

A lightweight, dependency-minimal Python process (`psutil` + `requests`) deployed on each monitored Linux host. It is intentionally **outbound-only**:

- It never opens a listening port and never accepts an inbound connection.
- Every 5 seconds it pushes a signed CPU/RAM/disk snapshot to the backend (`POST /api/metrics`).
- Every third cycle (~15s) it *polls* the backend for work via `GET /api/agent/metrics/sync` and `GET /api/agent/configs/sync` — "what should I run right now?" — then reports results back over `POST /api/agent/metrics/results` and `POST /api/agent/configs/report`.

This "agent asks, server answers" model means even on-demand actions (e.g. an operator clicking "Fetch Now" on a custom metric) are delivered through the same outbound heartbeat channel, so the security model never requires a listening port or stored SSH credentials on the agent side.

The agent runs as a locked-down, shell-less `monitoring_user` account (created during provisioning, see [§6](#6-deployment)), is started via `nohup`, and is kept alive across reboots by a per-user crontab `@reboot` entry — no root privileges are required at runtime.

### Backend / Monitoring Service (Spring Boot)

The backend is the heart of the system. On every inbound agent request it performs **one synchronous, authenticated write**, then **fans work out in parallel** so that none of the downstream consumers can block or slow down agent ingestion:

- **Database (PostgreSQL)** — the metric/config/event is persisted as the durable source of truth.
- **AI Module (AIOps / Ollama)** — a scheduled, asynchronous job layer (`@Scheduled`, Spring's `@Async` executor) independently reads recent data and runs correlation, root-cause, and threshold analysis against the local LLM — it never sits in the hot ingestion path.
- **Notification System (Email / in-app)** — SMTP dispatch is `@Async`-annotated (see `AsyncConfig`) so a slow or unreachable mail relay can never block a request thread; in-app notifications are written directly to the database and surfaced in the dashboard.

This deliberately parallel, non-blocking fan-out is what keeps a single unreachable downstream (a stalled AI job, a down SMTP relay) from ever creating backpressure on metric ingestion from hundreds of agents.

### Frontend

A server-rendered static frontend (`index.html`, `dashboard.html`, `metrics.html`, `configs.html`, `aiops.html`, `admin.html`, etc.) served either embedded from the Spring Boot jar (`src/main/resources/static/`) or standalone via a small Node/Express server (`frontend/server.js`) for local development. Authentication uses stateless JWT bearer tokens issued by `/api/auth/**`.

## 3. Security and Integrity Guarantees

### SHA-256 Configuration Hashing (Drift Detection)

Every file registered under **Config Tracker** is fingerprinted with SHA-256 (`_hash_file()` in `agent.py`). On each sync cycle the agent:

1. Re-hashes the local file.
2. Compares it against the `currentHash` the server told it about.
3. Reports one of three outcomes: `unchanged` (hash matches), a new hash + full content (hash differs or file is new), or a structured error (`FILE_NOT_FOUND` / `PERMISSION_DENIED`).

The **server is the sole authority** on what a hash change means:

- The **first** report for a file becomes its version-1 baseline (`ConfigFileStatus.IN_SYNC`).
- Any **subsequent** hash change against an existing baseline is classified as `DRIFT_DETECTED`, a full versioned diff is computed and stored (`DiffEngine`), admins and the file's creator are emailed and notified in-app, and the status **stays flagged** until a human explicitly calls `acceptBaseline()` — an "unchanged" report can never silently clear an unacknowledged drift.

This gives a complete, auditable version history per tracked file, viewable as a line-level diff in the dashboard's config history view.

### HMAC-SHA256 Request Authentication

All agent-to-backend traffic (`POST /api/metrics` and everything under `/api/agent/**`) is authenticated by `HmacAuthFilter`, a Spring Security filter that runs before any other authentication logic:

1. The agent computes `HMAC-SHA256(secretKey, timestamp + rawRequestBody)` and sends it as `X-Signature`, alongside `X-Timestamp` and `X-Server-Hostname`.
2. The backend looks up the target server's per-server secret key — stored **AES-256-GCM encrypted at rest** (`CryptoUtil`, key derived via SHA-256 from `app.security.master-key`) — decrypts it, and recomputes the same HMAC over the raw body it received.
3. The two signatures are compared using a **constant-time comparison** (`MessageDigest.isEqual`) to prevent timing side-channel attacks.
4. A **replay window** (`app.security.hmac-window-seconds`, default 300s) rejects any request whose timestamp has drifted too far from server time.
5. If the target server's status is `REVOKED`, every request is rejected with `403 Forbidden` — **this is also the kill switch**: the agent treats three consecutive `403` responses as a signal that its credentials were revoked and **self-terminates** (`sys.exit(0)`), so a decommissioned or compromised agent stops running without any manual cleanup on the host.

Each server receives a unique, cryptographically random 256-bit secret key (`SecureRandom`, generated during SSH provisioning) — a compromised key on one host has no effect on any other.

### Zero-Persistence Provisioning

Adding a server ("Sunucu Ekle") uses a single, one-time root SSH session (`SshProvisioningService` + JSch) to create/harden the restricted `monitoring_user` account, upload the agent, and start it. The root password supplied by the operator is used only in memory for that single request and is **never stored, logged, or reused** — it is zeroed out (`Arrays.fill(sshPassword, '\0')`) immediately after the session closes.

## 4. Offline AI Integration (AIOps)

A defining feature of this platform is that **anomaly detection and log analysis run entirely offline**. Despite the network having no internet access, the backend integrates with [**Ollama**](https://ollama.com), a local LLM runtime, over `http://127.0.0.1:11434` (or any reachable host on the closed network — no external API, no cloud model, no data ever leaves the corporate network).

`OllamaService` is a thin client around Ollama's `/api/generate` endpoint (default model `qwen2.5-coder:1.5b`, fully configurable), and `AIOpsService` builds the prompts that drive four scheduled or on-demand analyses:

| Job | Trigger | What it does |
|---|---|---|
| **Multi-metric correlation** | Every `app.aiops.scan-interval-ms` (default 5 min) | Feeds the latest CPU/RAM/disk readings of every tracked server to the model and asks it to flag abnormal correlations (e.g. high CPU with an idle disk queue, signs of resource exhaustion). |
| **Root-cause analysis (RCA)** | Same cadence | Feeds the last 10 failed custom-metric executions (stack traces / error output) to the model for a root-cause hypothesis; always persisted as `CRITICAL` and, if enabled, triggers an alert email. |
| **Critical-threshold watchdog** | Same cadence | Compares the latest reading per host against configurable CPU/RAM/disk thresholds (default 90%); on breach, asks the model for a short human-readable summary — falling back to a canned message if Ollama itself is unreachable, since threshold alerting must never depend on the AI being up. |
| **Daily health summary** | Cron `0 0 8 * * ?` (08:00 daily) | Summarizes the last 24h of metrics and failures into a short narrative report, optionally emailed. |

All four are scoped strictly to the metric groups an operator has explicitly opted into tracking (`AiOpsConfig.trackedGroupIds`) — if nothing is tracked, no background job runs and the chat interface says so rather than fabricating an answer from unrelated data. An embedded chat UI (`aiops.html`) lets operators ask ad-hoc questions against the same live system context. Every failure mode (connection refused, timeout, empty response) is wrapped in `OllamaUnavailableException` and handled gracefully — a down or not-yet-installed Ollama instance degrades AIOps features without ever affecting core monitoring or config-drift functionality.

## 5. Technology Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.3.2 (Web, Security, Data JPA, Validation, Mail) |
| Database | PostgreSQL 16, schema-versioned with Flyway |
| Auth | JWT (`jjwt` 0.12.6), BCrypt password hashing, optional email-based 2FA |
| Agent-backend transport security | HMAC-SHA256 request signing, AES-256-GCM secret-at-rest encryption |
| Provisioning | JSch (SSH) for zero-persistence remote agent bootstrap |
| Monitoring Agent | Python 3 (`psutil`, `requests`) |
| Offline AI | Ollama (local LLM runtime), default model `qwen2.5-coder:1.5b` |
| Frontend | Static HTML/CSS/vanilla JS, served embedded from Spring Boot or via a standalone Node/Express dev server |
| Build / Packaging | Maven, multi-stage Docker build (`maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre`) |
| Testing | JUnit 5, Spring Security Test, Testcontainers (PostgreSQL) |

## 6. Deployment

The target environment has **no internet access**, so every artifact — the application container, its dependencies, and the AI model — must be built or downloaded on a connected machine and transferred in physically or over an internal transfer mechanism.

### 6.1 Docker Image Transfer (Air-Gapped Pattern)

On a machine **with** internet access:

```bash
# Build the application image
docker compose build

# Export every image the compose stack needs to a single tarball
docker save -o monitoring-poc-images.tar \
  monitoring-poc-app \
  postgres:16-alpine
```

Transfer `monitoring-poc-images.tar` into the isolated network (approved offline media / internal file transfer), then on the target host:

```bash
# Load the images into the local Docker daemon - no registry pull required
docker load -i monitoring-poc-images.tar

# Bring the stack up from the pre-loaded images
docker compose up -d
```

`docker-compose.yml` provisions two services: `postgres` (PostgreSQL 16, persisted via the `pg_data` volume) and `app` (the Spring Boot backend, persisted via the `app_data` volume mounted at `/data01` for logs and generated agent artifacts).

Ollama must be installed and its model pulled (`ollama pull qwen2.5-coder:1.5b`) on a connected machine beforehand and transferred the same way (Ollama supports offline model bundling); the backend only needs a reachable Ollama HTTP endpoint at runtime, which can run on the same host or elsewhere on the internal network.

### 6.2 Environment Configuration

Copy `.env.example` to `.env` and fill in real values before starting the stack — **never deploy with the example defaults**:

```dotenv
# PostgreSQL credentials
DB_PASSWORD=CHANGE_ME

# 256-bit JWT signing secret (min 32 bytes, random)
JWT_SECRET=CHANGE_ME_min_32_bytes_random_value

# Passphrase used to derive the AES-256 key that encrypts per-server HMAC secrets at rest
MASTER_KEY=CHANGE_ME_random_passphrase_for_aes_key_derivation

# Bootstrap admin account, seeded once on first boot if no ADMIN exists yet
BOOTSTRAP_ADMIN_USERNAME=admin
BOOTSTRAP_ADMIN_EMAIL=admin@monitoring.local
BOOTSTRAP_ADMIN_PASSWORD=CHANGE_ME

# The address of THIS backend as reachable FROM the monitored hosts' network.
# localhost/127.0.0.1 will NOT work - agents run on remote targets.
APP_INGEST_BASE_URL=http://YOUR_SERVER_IP:8080
```

Additional variables consumed by `application.properties` (see file for full defaults): `OLLAMA_BASE_URL`, `OLLAMA_MODEL`, `MAIL_ENABLED`, `MAIL_FROM`, `HMAC_WINDOW_SECONDS`, `TWO_FA_ENABLED`, `AIOPS_SCAN_INTERVAL_MS`, `AIOPS_CRITICAL_CPU_THRESHOLD` / `_RAM_THRESHOLD` / `_DISK_THRESHOLD`.

### 6.3 Bare-Metal / systemd Deployment

For environments not using Docker, `scripts/monitoring-poc.service` and `scripts/monitoring-poc.env.example` provide a systemd unit and its `EnvironmentFile` template; install instructions are documented inline in the unit file. `scripts/bootstrap-target-host.sh` prepares a *new monitored target host* (installs `python3`, `pip3`, `cron`, and the agent's Python dependencies) — it must be run once per target host before that host can be added from the dashboard's "Sunucu Ekle" (Add Server) flow, which then performs the actual agent provisioning over SSH.

## 7. Use Cases and Alerting Triggers

The system raises alerts (persisted as `AiOpsAnomaly` / dashboard notifications and, where configured, delivered by email) under the following conditions, derived directly from the codebase:

- **Critical resource threshold breach** — CPU, RAM, or disk usage on any tracked host meets or exceeds its configured threshold (default **90%** for all three, `app.aiops.critical-*-threshold`). Triggers a `CRITICAL` anomaly card and, if alert email is enabled, a notification to the configured address — this check runs even if Ollama is unreachable, falling back to a templated message.
- **Configuration drift (hash mismatch)** — a tracked configuration file's live SHA-256 hash no longer matches its last-known baseline. Triggers `ConfigFileStatus.DRIFT_DETECTED`, a full versioned diff, and an immediate email + in-app notification to all admins and the file's original creator. The flag persists until an operator explicitly reviews and accepts the new baseline.
- **Configuration read failure** — a tracked file becomes unreadable on the target host (`FILE_NOT_FOUND` or `PERMISSION_DENIED`), surfaced as a distinct status in the config tracker UI.
- **Anomalous multi-metric correlation** — the AI module detects a statistically or behaviorally unusual relationship between CPU/RAM/disk on a host (e.g. high CPU with an idle disk queue), independent of any single hard threshold.
- **Custom metric / log-based failures** — a custom metric of type `HTTP_ENDPOINT`, `PORT_CHECK`, `LOG_PARSER`, or `CUSTOM_COMMAND` fails (unexpected HTTP status, unreachable port, non-matching log pattern, non-zero command exit). Failures feed the RCA job and are visible per metric group.
- **Revoked agent / self-destruct** — an agent receives three consecutive `403 Forbidden` responses (its server was marked `REVOKED`, e.g. after decommissioning), logs the condition, and terminates itself.
- **Workflow notifications** — new metric definition requests, metric approval/rejection, and profile change requests all raise `NotificationType` events for the relevant admins/users (operational rather than health-related, but part of the same notification pipeline).
- **Daily system health digest** — not itself an "alert," but a scheduled 08:00 summary of the previous 24 hours' metrics and failures, so degrading trends are visible even without a hard threshold breach.

## 8. Repository Notes

This is an internal, environment-specific project — some material should stay out of version control (or out of a *public* repository) regardless of how convenient it would be to commit. See the recommendation below.
