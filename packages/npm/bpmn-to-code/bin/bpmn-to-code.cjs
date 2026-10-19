#!/usr/bin/env node
"use strict";

// Starts the bpmn-to-code command-line tool with the Java runtime of the package for this platform.

const { spawnSync } = require("node:child_process");
const path = require("node:path");

const PLATFORM = `${process.platform}-${process.arch}`;
const PLATFORM_PACKAGE = `@miragon/bpmn-to-code-${PLATFORM}`;
const FAILURE = 1;

function platformPackageDir() {
  try {
    return path.dirname(require.resolve(`${PLATFORM_PACKAGE}/package.json`));
  } catch {
    console.error(`bpmn-to-code: ${PLATFORM_PACKAGE} is not installed. Supported platforms are darwin-arm64, darwin-x64, linux-arm64, linux-x64 and win32-x64; installing with --omit=optional leaves it out.`);
    process.exit(FAILURE);
  }
}

const packageDir = platformPackageDir();
const java = path.join(packageDir, "runtime", "bin", process.platform === "win32" ? "java.exe" : "java");
const classpath = path.join(packageDir, "lib", "*");
const jvmOptions = ["-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", "-Xshare:auto"];

const result = spawnSync(java, [...jvmOptions, "-cp", classpath, "io.miragon.bpmn.cli.MainKt", ...process.argv.slice(2)], { stdio: "inherit" });
if (result.error) {
  console.error(`bpmn-to-code: could not start ${java}: ${result.error.message}`);
  process.exit(FAILURE);
}
process.exit(result.status ?? FAILURE);
