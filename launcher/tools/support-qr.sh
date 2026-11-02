#!/bin/bash
# Redraws the source QR in res/drawable-nodpi from the source= line in res/raw/about.txt.
# This fork does not ship tip addresses.
set -euo pipefail
cd "$(dirname "$0")/.."
command -v qrencode >/dev/null || { echo "Install qrencode first." >&2; exit 1; }
qrencode -t PNG -s 12 -m 2 -l M -o res/drawable-nodpi/qr_source.png "$(sed -n 's/^source=//p' res/raw/about.txt)"
ls -l res/drawable-nodpi/qr_source.png
