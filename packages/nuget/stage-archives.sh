#!/bin/sh
set -eu

archives_dir=$1
tool_dir=$(cd "$(dirname "$0")/BpmnToCode.Tool" && pwd -P)

archive=$(find "$archives_dir" -name 'bpmn-to-code-*.zip' | head -n 1)
if [ -z "$archive" ]; then
  echo "no CLI archive in $archives_dir" >&2
  exit 1
fi

rm -rf "$tool_dir/cli"
staging=$(mktemp -d)
unzip -q "$archive" -d "$staging"
mkdir -p "$tool_dir/cli"
mv "$staging"/*/lib "$tool_dir/cli/lib"
rm -rf "$staging"
