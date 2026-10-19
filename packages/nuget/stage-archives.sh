#!/bin/sh
set -eu

archives_dir=$1
tool_dir=$(cd "$(dirname "$0")/BpmnToCode.Tool" && pwd -P)

rid_of() {
  case $1 in
    linux-x64) echo linux-x64 ;;
    linux-arm64) echo linux-arm64 ;;
    darwin-x64) echo osx-x64 ;;
    darwin-arm64) echo osx-arm64 ;;
    win32-x64) echo win-x64 ;;
    *) echo "unknown platform $1" >&2; exit 1 ;;
  esac
}

rm -rf "$tool_dir/cli"
for archive in "$archives_dir"/bpmn-to-code-*.zip; do
  platform=$(basename "$archive" .zip | sed -E 's/^bpmn-to-code-.+-([a-z0-9]+-[a-z0-9]+)$/\1/')
  target="$tool_dir/cli/$(rid_of "$platform")"
  staging=$(mktemp -d)
  unzip -q "$archive" -d "$staging"
  mkdir -p "$target"
  mv "$staging"/*/* "$target"
  rm -rf "$staging"
done
