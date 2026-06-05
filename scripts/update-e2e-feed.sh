#!/usr/bin/env bash
# Local end-to-end harness for the app self-update flow (N4).
#
# Generates a fake update feed (latest-*.yml + a dummy installer), serves it over
# HTTP, and prints the command to launch Zayit against it. Nothing is installed:
# pass DRY_RUN so installAndRestart/installAndQuit only log.
#
# Usage:
#   scripts/update-e2e-feed.sh [CURRENT_VERSION] [NEW_VERSION] [PORT]
# Examples:
#   scripts/update-e2e-feed.sh 1.0.0 1.1.0      # MINOR  → titlebar icon + dialog + DB warning
#   scripts/update-e2e-feed.sh 1.0.0 1.0.1      # PATCH  → pre-download (silent close on Win/Mac, dialog on Linux)
#   scripts/update-e2e-feed.sh 1.0.0 2.0.0      # MAJOR  → titlebar icon + dialog + DB warning
set -euo pipefail

CURRENT_VERSION="${1:-1.0.0}"
NEW_VERSION="${2:-1.1.0}"
PORT="${3:-8077}"

case "$(uname -s)" in
  Darwin) YML="latest-mac.yml"; EXT="zip"; OSPART="mac" ;;
  MINGW*|MSYS*|CYGWIN*) YML="latest.yml"; EXT="exe"; OSPART="windows" ;;
  *) YML="latest-linux.yml"; EXT="deb"; OSPART="linux" ;;
esac
case "$(uname -m)" in
  arm64|aarch64) ARCH="arm64" ;;
  *) ARCH="amd64" ;;
esac

FEED_DIR="$(mktemp -d -t zayit-feed-XXXX)"
INSTALLER="zayit-${NEW_VERSION}-${OSPART}-${ARCH}.${EXT}"

# Dummy installer payload (never executed thanks to DRY_RUN).
head -c 1048576 /dev/urandom > "${FEED_DIR}/${INSTALLER}"
SHA512="$(openssl dgst -sha512 -binary "${FEED_DIR}/${INSTALLER}" | base64 | tr -d '\n')"
SIZE="$(wc -c < "${FEED_DIR}/${INSTALLER}" | tr -d ' ')"

cat > "${FEED_DIR}/${YML}" <<EOF
version: ${NEW_VERSION}
files:
  - url: ${INSTALLER}
    sha512: ${SHA512}
    size: ${SIZE}
releaseDate: '2026-06-04T10:00:00.000Z'
EOF

echo "Feed ready in ${FEED_DIR}"
echo "  ${YML} (version ${NEW_VERSION})"
echo "  ${INSTALLER} (${SIZE} bytes)"
echo
echo "In another terminal, launch Zayit against this feed:"
echo
echo "  SEFORIMAPP_UPDATE_FEED_URL=http://127.0.0.1:${PORT} \\"
echo "  SEFORIMAPP_UPDATE_FORCE_VERSION=${CURRENT_VERSION} \\"
echo "  SEFORIMAPP_UPDATE_EXECUTABLE_TYPE=${EXT} \\"
echo "  SEFORIMAPP_UPDATE_DRY_RUN=1 \\"
echo "  ./gradlew :SeforimApp:run"
echo
echo "Serving on http://127.0.0.1:${PORT} (Ctrl+C to stop)…"
exec python3 -m http.server "${PORT}" --bind 127.0.0.1 --directory "${FEED_DIR}"
