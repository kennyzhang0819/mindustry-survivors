#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
version=$(sed -n 's/^version: "\(.*\)"/\1/p' mod.hjson)
tag="v$version"

if [ -n "$(git status --porcelain)" ]; then
  echo "You have changes that aren't committed. Commit them first."
  exit 1
fi
git fetch -q origin
if [ "$(git rev-parse HEAD)" != "$(git rev-parse origin/main)" ]; then
  echo "Your commits aren't on GitHub yet (or you're behind it). Push or pull first."
  exit 1
fi
if git ls-remote --tags origin "$tag" | grep -q .; then
  echo "$tag is already released. Raise version in mod.hjson, commit, push and run this again."
  exit 1
fi

./build.sh

echo
echo "Ready to release $tag. Now:"
echo "1. Open https://github.com/kennyzhang0819/mindustry-survivors/releases/new?tag=$tag&title=Mindustry%20Survivors%20$version"
echo "2. Write a few lines about what's new."
echo "3. Drag in $(pwd)/build/MindustrySurvivors.jar"
echo "4. Press Publish release."
