#!/usr/bin/env bash
# ==========================================================================
# One-time, root-run PACKAGE bootstrap for a NEW monitored target host.
#
# As of the root-based provisioning flow, "Sunucu Ekle" in the dashboard
# already creates/hardens monitoring_user (nologin/false shell, no
# interactive session possible), owns /data01/monitoring, uploads the agent,
# and starts it - all over a single root SSH session whose password is used
# once and never stored. You do NOT need to run this script for account
# creation anymore.
#
# What's still a host prerequisite the app does not do for you: having
# python3/pip3 and cron/cronie installed, and the agent's pip dependencies
# available. Run this once per target host to cover that.
#
# Usage: ./bootstrap-target-host.sh
# ==========================================================================
set -euo pipefail

if [[ "$(id -u)" -ne 0 ]]; then
    echo "Bu script root olarak calistirilmalidir." >&2
    exit 1
fi

echo "==> Sistem paketleri kuruluyor (python3, pip3, cron)..."
if command -v apt-get &>/dev/null; then
    apt-get update -qq
    apt-get install -y -qq python3 python3-pip cron
elif command -v yum &>/dev/null; then
    yum install -y -q python3 python3-pip cronie
    systemctl enable --now crond 2>/dev/null || true
else
    echo "    Bilinmeyen paket yoneticisi - python3/pip3/cron kurulu oldugundan emin olun." >&2
fi

echo "==> Python agent bagimliliklari (psutil, requests) sistem genelinde kuruluyor..."
pip3 install --quiet psutil requests

echo "==> Tamamlandi. Artik dashboard'daki 'Sunucu Ekle' modalindan bu sunucuyu"
echo "    IP=$(hostname -I | awk '{print $1}') ve SSH kullanicisi=root ile ekleyebilirsiniz."
echo "    Root sifresi yalnizca o tek kurulum istegi sirasinda kullanilir, hicbir yerde"
echo "    saklanmaz; monitoring_user hesabi ve dosya izinleri otomatik olarak ayarlanir."
