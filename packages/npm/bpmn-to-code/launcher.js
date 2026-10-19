#!/usr/bin/env node
"use strict";

const { spawnSync } = require("node:child_process");
const fs = require("node:fs");
const path = require("node:path");

const platform = `${process.platform}-${process.arch}`;
const cliDirectory = resolveCliDirectory();
const java = path.join(cliDirectory, "runtime", "bin", process.platform === "win32" ? "java.exe" : "java");

if (process.platform !== "win32") {
  markExecutable(java);
  markExecutable(path.join(cliDirectory, "runtime", "lib", "jspawnhelper"));
}

const javaArguments = ["-cp", path.join(cliDirectory, "lib", "*"), "io.miragon.bpmn.cli.MainKt", ...process.argv.slice(2)];
const result = spawnSync(java, javaArguments, { stdio: "inherit" });
if (result.error) {
  console.error(`bpmn-to-code: could not start ${java}: ${result.error.message}`);
  process.exit(1);
}
process.exit(result.status ?? 1);

function resolveCliDirectory() {
  try {
    return path.dirname(require.resolve(`@miragon/bpmn-to-code-${platform}/package.json`));
  } catch {
    console.error(`bpmn-to-code: no package for ${platform}. It is an optional dependency; was it installed with --omit=optional?`);
    process.exit(1);
  }
}

function markExecutable(file) {
  const mode = fs.statSync(file).mode;
  if ((mode & 0o111) !== 0o111) {
    fs.chmodSync(file, mode | 0o111);
  }
}
