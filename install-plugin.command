#!/usr/bin/env bash
# macOS: double-click in Finder to install the Local Push Mock plugin into Android Studio.
cd "$(dirname "$0")"
bash scripts/install-plugin.sh "$@"
status=$?
echo
read -n 1 -s -r -p "Press any key to close…"
exit $status
