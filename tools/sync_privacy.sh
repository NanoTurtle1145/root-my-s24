#!/bin/sh
# 把仓库里的 docs/PRIVACY.md 同步进 App assets（App 内「隐私政策」页渲染这一份）。
# 改完政策后跑一次，避免 GitHub 上的版本和 App 里的版本对不上。
set -eu
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cp "$ROOT/docs/PRIVACY.md" "$ROOT/app/src/main/assets/privacy.md"
echo "synced: docs/PRIVACY.md -> app/src/main/assets/privacy.md"
