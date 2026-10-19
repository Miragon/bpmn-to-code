import { execFileSync } from "node:child_process";
import { cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { basename, dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const [archivesDirectory, version] = process.argv.slice(2);
if (!archivesDirectory || !version) {
  console.error("usage: node stage-packages.mjs <archives-directory> <version>");
  process.exit(2);
}

const packagesDirectory = dirname(fileURLToPath(import.meta.url));
const license = join(packagesDirectory, "..", "..", "LICENSE");
const distDirectory = join(packagesDirectory, "dist");
const platforms = {
  "darwin-arm64": { os: "darwin", cpu: "arm64" },
  "darwin-x64": { os: "darwin", cpu: "x64" },
  "linux-arm64": { os: "linux", cpu: "arm64" },
  "linux-x64": { os: "linux", cpu: "x64" },
  "win32-x64": { os: "win32", cpu: "x64" },
};

rmSync(distDirectory, { recursive: true, force: true });
stageRuntimePackage();
stageMainPackage();
for (const archive of readdirSync(archivesDirectory).filter((name) => name.endsWith(".zip"))) {
  stagePlatformPackage(join(archivesDirectory, archive));
}

function stageMainPackage() {
  const target = join(distDirectory, "bpmn-to-code");
  cpSync(join(packagesDirectory, "bpmn-to-code"), target, { recursive: true });
  const manifest = readJson(join(target, "package.json"));
  manifest.version = version;
  for (const dependency of Object.keys(manifest.optionalDependencies)) {
    manifest.optionalDependencies[dependency] = version;
  }
  for (const dependency of Object.keys(manifest.peerDependencies)) {
    manifest.peerDependencies[dependency] = version;
  }
  writeJson(join(target, "package.json"), manifest);
  cpSync(license, join(target, "LICENSE"));
}

function stageRuntimePackage() {
  const source = join(packagesDirectory, "bpmn-to-code-runtime");
  if (!existsSync(join(source, "dist"))) {
    console.error("bpmn-to-code-runtime has no dist; run npm ci and npm run build in it first");
    process.exit(1);
  }
  const target = join(distDirectory, "bpmn-to-code-runtime");
  mkdirSync(target, { recursive: true });
  for (const part of ["dist", "README.md"]) {
    cpSync(join(source, part), join(target, part), { recursive: true });
  }
  const manifest = readJson(join(source, "package.json"));
  manifest.version = version;
  delete manifest.scripts;
  delete manifest.devDependencies;
  writeJson(join(target, "package.json"), manifest);
  cpSync(license, join(target, "LICENSE"));
}

function stagePlatformPackage(archive) {
  const platform = basename(archive, ".zip").match(/-([a-z0-9]+-[a-z0-9]+)$/)[1];
  const { os, cpu } = platforms[platform];
  const target = join(distDirectory, `bpmn-to-code-${platform}`);
  const unpacked = mkdtempSync(join(tmpdir(), "bpmn-to-code-"));
  execFileSync("unzip", ["-q", archive, "-d", unpacked]);
  const [rootFolder] = readdirSync(unpacked);
  mkdirSync(target, { recursive: true });
  for (const part of ["lib", "runtime"]) {
    cpSync(join(unpacked, rootFolder, part), join(target, part), { recursive: true });
  }
  rmSync(unpacked, { recursive: true, force: true });

  const template = readFileSync(join(packagesDirectory, "platform", "package.template.json"), "utf8");
  const manifest = JSON.parse(template.replaceAll("PLATFORM", platform));
  manifest.version = version;
  manifest.os = [os];
  manifest.cpu = [cpu];
  writeJson(join(target, "package.json"), manifest);
  cpSync(license, join(target, "LICENSE"));
  const readme = readFileSync(join(packagesDirectory, "platform", "README.template.md"), "utf8");
  writeFileSync(join(target, "README.md"), readme.replaceAll("PLATFORM", platform));
}

function readJson(file) {
  return JSON.parse(readFileSync(file, "utf8"));
}

function writeJson(file, content) {
  writeFileSync(file, `${JSON.stringify(content, null, 2)}\n`);
}
