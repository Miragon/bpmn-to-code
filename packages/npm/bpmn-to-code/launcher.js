#!/usr/bin/env node
"use strict";

const { spawnSync } = require("node:child_process");
const fs = require("node:fs");
const path = require("node:path");

const REQUIRED_JAVA_VERSION = 21;
const java = resolveJava();
requireJavaVersion(java);

const javaArguments = ["-cp", path.join(__dirname, "lib", "*"), "io.miragon.bpmn.cli.MainKt", ...process.argv.slice(2)];
const result = spawnSync(java, javaArguments, { stdio: "inherit" });
if (result.error) {
  fail(`could not start ${java}: ${result.error.message}`);
}
if (result.signal) {
  process.kill(process.pid, result.signal);
}
process.exit(result.status ?? 1);

function resolveJava() {
  const executable = process.platform === "win32" ? "java.exe" : "java";
  const javaHome = process.env.JAVA_HOME;
  if (javaHome && fs.existsSync(path.join(javaHome, "bin", executable))) {
    return path.join(javaHome, "bin", executable);
  }
  return "java";
}

function requireJavaVersion(javaCommand) {
  const probe = spawnSync(javaCommand, ["-version"], { encoding: "utf8" });
  if (probe.error) {
    fail(`needs Java ${REQUIRED_JAVA_VERSION} or newer; set JAVA_HOME or put java on the PATH`);
  }
  const version = majorVersionOf(probe.stderr);
  if (version < REQUIRED_JAVA_VERSION) {
    fail(`needs Java ${REQUIRED_JAVA_VERSION} or newer, ${javaCommand} is Java ${version}`);
  }
}

function majorVersionOf(versionOutput) {
  const [, first, second] = versionOutput.match(/version "(\d+)(?:\.(\d+))?/) ?? [];
  if (first === "1") {
    return Number(second);
  }
  return Number(first ?? 0);
}

function fail(message) {
  console.error(`bpmn-to-code: ${message}`);
  process.exit(1);
}
