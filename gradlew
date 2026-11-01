#!/bin/sh
# OTE custom verified-distribution bootstrap; no official Wrapper JAR is bundled.
set -eu
OTE_SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if command -v python3 >/dev/null 2>&1; then
    exec python3 "$OTE_SCRIPT_DIR/scripts/gradle_bootstrap.py" "$@"
fi
if command -v python >/dev/null 2>&1; then
    exec python "$OTE_SCRIPT_DIR/scripts/gradle_bootstrap.py" "$@"
fi
echo 'Python 3.9+ is required by this source distribution bootstrap. See docs/BUILD.md.' >&2
exit 1
