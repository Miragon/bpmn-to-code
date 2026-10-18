#!/usr/bin/env bash
# Smoke test of a packaged bpmn-to-code: the start script of an archive, the Homebrew command or any
# other command line that starts the CLI. Generates and validates the shared Zeebe models.
#
#   bpmn-to-code-cli/scripts/smoke-test.sh smoke/bpmn-to-code-6.4.0-linux-x64/bin/bpmn-to-code
#   bpmn-to-code-cli/scripts/smoke-test.sh "$(brew --prefix)/bin/bpmn-to-code"
#
# With EXPECTED_VERSION set, --version has to print that version.
set -euo pipefail

if [ "$#" -eq 0 ]; then
  echo "usage: $0 <command that starts bpmn-to-code...>" >&2
  exit 2
fi

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
models="$root/shared/bpmn/zeebe"
output="$(mktemp -d)"
trap 'rm -rf "$output"' EXIT

version="$("$@" --version)"
if [ -n "${EXPECTED_VERSION:-}" ] && [ "$version" != "bpmn-to-code version ${EXPECTED_VERSION}" ]; then
  echo "expected version ${EXPECTED_VERSION}, got: ${version}" >&2
  exit 1
fi

"$@" generate-api --baseDir "$models" --filePattern '*.bpmn' --processEngine ZEEBE \
  --outputLanguage KOTLIN --outputFolderPath "$output" --packagePath io.miravelo.process
test -f "$output/io/miravelo/process/BikeLeasingProcessApi.kt"

"$@" validate --baseDir "$models" --filePattern '*.bpmn' --processEngine ZEEBE

echo "smoke test of $* passed"
