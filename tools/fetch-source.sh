#!/usr/bin/env bash
# Keep VERSION in sync with the jar version in build.sh.
set -e
cd "$(dirname "$0")/.."
VERSION=v160.7
DEST=libs/mindustry-src

if [ -d "$DEST/core/src/mindustry" ]; then
  echo "Mindustry source already in $DEST"
  exit 0
fi

mkdir -p libs
echo "Cloning Mindustry $VERSION (shallow) into $DEST..."
git clone --depth 1 --branch "$VERSION" https://github.com/Anuken/Mindustry.git "$DEST"
echo "Done. Game code is in $DEST/core/src/mindustry"
