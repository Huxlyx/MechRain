#!/usr/bin/env bash
# update-server.sh — Download the latest mechrain-server release, update the
# symlink and restart the systemd service.

set -euo pipefail

REPO="Huxlyx/MechRain"
INSTALL_DIR="/mnt/ssd/mechrain-server"
SYMLINK="$INSTALL_DIR/current.jar"
SERVICE="mechrain-server"
SERVICE_USER="mechrain-server"
JAR_PATTERN="mechrain-server-*-all.jar"

# ── helpers ──────────────────────────────────────────────────────────────────

need_cmd() { command -v "$1" &>/dev/null || { echo "ERROR: '$1' is required but not installed." >&2; exit 1; }; }

# Run a command as SERVICE_USER, or directly if already running as that user or as root.
run_as_service_user() {
  if [[ "$(id -u)" -eq 0 ]]; then
    runuser -u "$SERVICE_USER" -- "$@"
  elif [[ "$(id -un)" == "$SERVICE_USER" ]]; then
    "$@"
  else
    sudo -u "$SERVICE_USER" "$@"
  fi
}

# Run a privileged command, using sudo only when not already root.
run_privileged() {
  if [[ "$(id -u)" -eq 0 ]]; then
    "$@"
  else
    sudo "$@"
  fi
}

# ── preflight ────────────────────────────────────────────────────────────────

need_cmd curl
need_cmd jq

# ── resolve latest release ───────────────────────────────────────────────────

echo "Fetching latest release info from GitHub..."
RELEASE_JSON=$(curl -fsSL "https://api.github.com/repos/$REPO/releases/latest")
TAG=$(echo "$RELEASE_JSON" | jq -r '.tag_name')
DOWNLOAD_URL=$(echo "$RELEASE_JSON" | jq -r \
  '.assets[] | select(.name | test("mechrain-server-.*-all\\.jar")) | .browser_download_url')

if [[ -z "$DOWNLOAD_URL" ]]; then
  echo "ERROR: No server jar found in release $TAG." >&2
  exit 1
fi

JAR_NAME=$(basename "$DOWNLOAD_URL")
TARGET="$INSTALL_DIR/$JAR_NAME"

echo "Latest release : $TAG"
echo "Artifact       : $JAR_NAME"

# ── skip if already current ──────────────────────────────────────────────────

if [[ -f "$TARGET" ]] && [[ "$(readlink -f "$SYMLINK" 2>/dev/null)" == "$TARGET" ]]; then
  echo "Already up-to-date. Nothing to do."
  exit 0
fi

# ── download ─────────────────────────────────────────────────────────────────

echo "Downloading..."
run_as_service_user curl -fL --progress-bar "$DOWNLOAD_URL" -o "$TARGET"
echo "Download complete: $TARGET"

# ── update symlink ────────────────────────────────────────────────────────────

run_as_service_user ln -sfn "$JAR_NAME" "$SYMLINK"
echo "Symlink updated: $SYMLINK -> $JAR_NAME"

# ── restart service ───────────────────────────────────────────────────────────

echo "Restarting $SERVICE..."
run_privileged systemctl restart "$SERVICE"
echo "Done. Service restarted successfully."
