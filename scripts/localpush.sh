#!/usr/bin/env bash
# Sends a local push without the plugin (terminal / CI). Same protocol as the plugin.
#   ./localpush.sh -p com.myapp.debug [-s emulator-5554] payload.json
set -euo pipefail

usage() { echo "usage: $0 -p <applicationId> [-s <serial>] <payload.json>" >&2; exit 64; }

pkg="" serial=""
while getopts "p:s:h" opt; do
  case "$opt" in
    p) pkg="$OPTARG" ;;
    s) serial="$OPTARG" ;;
    *) usage ;;
  esac
done
shift $((OPTIND - 1))
[[ -n "$pkg" && $# -eq 1 && -f "$1" ]] || usage
[[ "$pkg" =~ ^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$ ]] || { echo "invalid applicationId: $pkg" >&2; exit 65; }

ADB="${ADB:-adb}"
payload_b64="$(base64 < "$1" | tr -d '\n')"

"$ADB" ${serial:+-s "$serial"} shell am broadcast \
  -a com.localpush.debug.RECEIVE_LOCAL_PUSH \
  -n "$pkg/com.localpush.debug.LocalPushReceiver" \
  --include-stopped-packages \
  --es payload_b64 "$payload_b64"
