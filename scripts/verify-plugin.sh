#!/usr/bin/env bash
# Verifies the already built plugin against your local Android Studio with the official
# IntelliJ Plugin Verifier (the same tool JetBrains Marketplace uses to review uploads).
#
#   scripts/verify-plugin.sh                                  -> Android Studio in /Applications
#   scripts/verify-plugin.sh "/path/to/Android Studio.app"    -> another IDE (on Linux: its root folder)
#
# Requires running the installer or `plugin/gradlew buildPlugin` first.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IDE="${1:-/Applications/Android Studio.app}"
[[ -d "$IDE/Contents" ]] && IDE_HOME="$IDE/Contents" || IDE_HOME="$IDE" # macOS: .app/Contents

JAVA="$IDE_HOME/jbr/Contents/Home/bin/java"
[[ -x "$JAVA" ]] || JAVA="$IDE_HOME/jbr/bin/java"
[[ -x "$JAVA" ]] || JAVA="java"

ZIP="$(ls -t "$ROOT"/plugin/build/distributions/*.zip 2>/dev/null | head -1)"
[[ -n "$ZIP" ]] || { echo "No .zip found: run the installer or plugin/gradlew buildPlugin first" >&2; exit 1; }

VERIFIER="$(find "$HOME/.gradle/caches" -name 'verifier-cli-*-all.jar' 2>/dev/null | sort -V | tail -1)"
if [[ -z "$VERIFIER" ]]; then
  echo "Downloading the Plugin Verifier…"
  (cd "$ROOT/plugin" && JAVA_HOME="$(dirname "$(dirname "$JAVA")")" ./gradlew -q dependencies --configuration intellijPluginVerifier >/dev/null)
  VERIFIER="$(find "$HOME/.gradle/caches" -name 'verifier-cli-*-all.jar' | sort -V | tail -1)"
fi

echo "Plugin: $ZIP"
echo "IDE:    $IDE_HOME"
"$JAVA" -jar "$VERIFIER" check-plugin "$ZIP" "$IDE_HOME" \
  -verification-reports-dir "$ROOT/plugin/build/reports/pluginVerifier" 2>&1 \
  | grep -vE "INFO|Layout component|^\s*$"
