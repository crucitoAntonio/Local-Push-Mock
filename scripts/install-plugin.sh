#!/usr/bin/env bash
# Local Push Mock plugin installer for macOS and Linux.
#
# Does all of "Part A" of the user guide:
#   1. Detects Android Studio installations.
#   2. Uses the Java 21 bundled with Android Studio (JBR): no JDK install needed.
#   3. Builds the plugin with the bundled Gradle Wrapper: no Gradle install needed.
#   4. Copies the plugin into the plugins folder of every Android Studio found.
#
# Usage:
#   scripts/install-plugin.sh                     build and install
#   scripts/install-plugin.sh --zip plugin.zip    install a prebuilt .zip (no build)
#   scripts/install-plugin.sh --uninstall         uninstall
#   scripts/install-plugin.sh --dest <dir>        install into a specific plugins folder
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PLUGIN_PROJECT="$ROOT/plugin"
PLUGIN_FOLDER="local-push-mock" # root folder inside the .zip
MIN_JAVA=21

ZIP="" DEST="" UNINSTALL=0

if [[ -t 1 ]]; then B=$'\e[1m' G=$'\e[32m' Y=$'\e[33m' R=$'\e[31m' N=$'\e[0m'; else B="" G="" Y="" R="" N=""; fi
step() { printf '\n%s==> %s%s\n' "$B" "$1" "$N"; }
ok()   { printf '  %s[ok]%s    %s\n' "$G" "$N" "$1"; }
warn() { printf '  %s[warn]%s  %s\n' "$Y" "$N" "$1"; }
fail() { printf '\n  %s[error]%s %s\n' "$R" "$N" "$1" >&2; exit 1; }

usage() { sed -n '2,15p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --zip) ZIP="${2:?missing .zip path}"; shift 2 ;;
    --dest) DEST="${2:?missing folder}"; shift 2 ;;
    --uninstall) UNINSTALL=1; shift ;;
    -h|--help) usage ;;
    *) fail "Unknown option: $1 (use --help)" ;;
  esac
done

case "$(uname -s)" in
  Darwin) OS=mac ;;
  Linux) OS=linux ;;
  *) fail "Unsupported OS. On Windows use install-plugin.cmd" ;;
esac

# ---------------------------------------------------------------- Android Studio

# Prints one Android Studio installation path per line.
find_studio_installs() {
  local candidates=()
  if [[ $OS == mac ]]; then
    candidates=(/Applications/Android\ Studio*.app "$HOME"/Applications/Android\ Studio*.app)
  else
    candidates=(/opt/android-studio* /usr/local/android-studio* "$HOME"/android-studio*
                /snap/android-studio/current/android-studio
                "$HOME"/.local/share/JetBrains/Toolbox/apps/android-studio*)
  fi
  local dir
  for dir in "${candidates[@]}"; do
    [[ -f "$(product_info "$dir")" ]] && echo "$dir"
  done
  return 0
}

product_info() { [[ $OS == mac ]] && echo "$1/Contents/Resources/product-info.json" || echo "$1/product-info.json"; }
jbr_home()     { [[ $OS == mac ]] && echo "$1/Contents/jbr/Contents/Home" || echo "$1/jbr"; }

# "AndroidStudio2025.2.1": name of that version's configuration folder.
data_dir_name() {
  sed -n 's/.*"dataDirectoryName"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$(product_info "$1")" | head -1
}

plugins_dir_for() {
  if [[ $OS == mac ]]; then
    echo "$HOME/Library/Application Support/Google/$1/plugins"
  else
    echo "${XDG_DATA_HOME:-$HOME/.local/share}/Google/$1" # on Linux plugins go directly here
  fi
}

# Major version of a JDK, read from its `release` file (JAVA_VERSION="21.0.8").
java_major() {
  [[ -f "$1/release" ]] || { echo 0; return; }
  local v
  v="$(sed -n 's/^JAVA_VERSION="\([0-9]*\).*/\1/p' "$1/release")"
  echo "${v:-0}"
}

step "Looking for Android Studio"
INSTALLS=()
while IFS= read -r line; do INSTALLS+=("$line"); done < <(find_studio_installs)

TARGETS=()
if [[ -n "$DEST" ]]; then
  TARGETS=("$DEST")
  ok "Manual destination: $DEST"
else
  for install in ${INSTALLS[@]+"${INSTALLS[@]}"}; do
    name="$(data_dir_name "$install")"
    [[ -n "$name" ]] || continue
    TARGETS+=("$(plugins_dir_for "$name")")
    ok "$name  ($install)"
  done
  [[ ${#TARGETS[@]} -gt 0 ]] || fail "Android Studio not found. Install it or pass the plugins folder with --dest <dir>."
fi

# ---------------------------------------------------------------- uninstall

if [[ $UNINSTALL -eq 1 ]]; then
  step "Uninstalling"
  for dir in "${TARGETS[@]}"; do
    if [[ -d "$dir/$PLUGIN_FOLDER" ]]; then rm -rf "${dir:?}/$PLUGIN_FOLDER"; ok "Removed from $dir"
    else warn "Not installed in $dir"; fi
  done
  printf '\n%s==> Done.%s Restart Android Studio.\n' "$G$B" "$N"
  exit 0
fi

# ---------------------------------------------------------------- build

if [[ -z "$ZIP" ]]; then
  step "Looking for Java $MIN_JAVA+"
  JAVA=""
  if [[ -n "${JAVA_HOME:-}" && $(java_major "$JAVA_HOME") -ge $MIN_JAVA ]]; then
    JAVA="$JAVA_HOME"
  else
    for install in ${INSTALLS[@]+"${INSTALLS[@]}"}; do
      jbr="$(jbr_home "$install")"
      if [[ $(java_major "$jbr") -ge $MIN_JAVA ]]; then JAVA="$jbr"; break; fi
    done
  fi
  [[ -n "$JAVA" ]] || fail "No Java $MIN_JAVA+ found. Update Android Studio (Ladybug or later) or set JAVA_HOME."
  ok "Java $(java_major "$JAVA"): $JAVA"

  step "Building the plugin (the first run downloads dependencies and may take a few minutes)"
  [[ -x "$PLUGIN_PROJECT/gradlew" ]] || chmod +x "$PLUGIN_PROJECT/gradlew"
  (cd "$PLUGIN_PROJECT" && JAVA_HOME="$JAVA" ./gradlew buildPlugin --console=plain --warning-mode=none) \
    || fail "Build failed (see the error above)."
  ZIP="$(ls -t "$PLUGIN_PROJECT"/build/distributions/"$PLUGIN_FOLDER"-*.zip 2>/dev/null | grep -v -- '-signed.zip' | head -1)"
  [[ -n "$ZIP" ]] || fail "The plugin .zip was not generated."
  ok "Built: $ZIP"
fi

[[ -f "$ZIP" ]] || fail "File not found: $ZIP"

# ---------------------------------------------------------------- install

step "Installing"
command -v unzip >/dev/null || fail "'unzip' is missing (Linux: sudo apt install unzip)."
for dir in "${TARGETS[@]}"; do
  mkdir -p "$dir"
  rm -rf "${dir:?}/$PLUGIN_FOLDER" # replaces previous versions
  unzip -q -o "$ZIP" -d "$dir"
  [[ -d "$dir/$PLUGIN_FOLDER/lib" ]] || fail "The .zip does not have the expected layout ($PLUGIN_FOLDER/lib)."
  ok "Installed in $dir"
done

printf '\n%s==> Plugin installed.%s\n' "$G$B" "$N"
# Only the IDE process (not the Gradle daemon, which also uses Android Studio's Java).
if pgrep -f '/Contents/MacOS/studio$|/bin/studio(\.sh)?( |$)|idea\.platform\.prefix=AndroidStudio' >/dev/null 2>&1; then
  warn "Android Studio is running: quit and reopen it to load the plugin."
else
  ok "Open Android Studio: the \"Local Push\" tool window is on the right side."
fi
