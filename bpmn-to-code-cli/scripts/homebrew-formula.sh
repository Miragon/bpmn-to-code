#!/usr/bin/env bash
# Writes the Homebrew formula of bpmn-to-code (Miragon/homebrew-tap, Formula/bpmn-to-code.rb) to
# stdout: the CLI archives of one GitHub release for macOS and Linux, each with its own Java runtime,
# checksums from the SHA256SUMS of that release.
#
#   gh release download v6.5.0 --pattern SHA256SUMS
#   bpmn-to-code-cli/scripts/homebrew-formula.sh 6.5.0 SHA256SUMS > bpmn-to-code.rb
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <version> <SHA256SUMS file>" >&2
  exit 2
fi

version="$1"
checksums="$2"
base="https://github.com/Miragon/bpmn-to-code/releases/download/v${version}"

checksum() {
  local sum
  sum="$(awk -v file="$1" '$2 == file || $2 == "*" file { print $1 }' "$checksums")"
  if [ -z "$sum" ]; then
    echo "$checksums has no checksum of $1" >&2
    exit 1
  fi
  echo "$sum"
}

archive() {
  echo "bpmn-to-code-${version}-$1.zip"
}

cat <<FORMULA
class BpmnToCode < Formula
  desc "Generates type-safe APIs from BPMN process models and validates them"
  homepage "https://github.com/Miragon/bpmn-to-code"
  license "MIT"

  on_macos do
    on_arm do
      url "${base}/$(archive darwin-arm64)"
      sha256 "$(checksum "$(archive darwin-arm64)")"
    end
    on_intel do
      url "${base}/$(archive darwin-x64)"
      sha256 "$(checksum "$(archive darwin-x64)")"
    end
  end

  on_linux do
    on_arm do
      url "${base}/$(archive linux-arm64)"
      sha256 "$(checksum "$(archive linux-arm64)")"
    end
    on_intel do
      url "${base}/$(archive linux-x64)"
      sha256 "$(checksum "$(archive linux-x64)")"
    end
  end

  def install
    libexec.install Dir["*"]
    bin.write_exec_script libexec/"bin/bpmn-to-code"
  end

  test do
    assert_equal "bpmn-to-code version #{version}", shell_output("#{bin}/bpmn-to-code --version").strip
  end
end
FORMULA
