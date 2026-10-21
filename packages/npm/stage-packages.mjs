import { execFileSync } from "node:child_process";
import { cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const [archivesDirectory, version] = process.argv.slice(2);
if (!archivesDirectory || !version) {
  console.error("usage: node stage-packages.mjs <archives-directory> <version>");
  process.exit(2);
}

const packagesDirectory = dirname(fileURLToPath(import.meta.url));
const license = join(packagesDirectory, "..", "..", "LICENSE");
const distDirectory = join(packagesDirectory, "dist");

const archive = readdirSync(archivesDirectory).find((name) => name.endsWith(".zip"));
if (!archive) {
  console.error(`no CLI archive in ${archivesDirectory}`);
  process.exit(1);
}

rmSync(distDirectory, { recursive: true, force: true });
stageRuntimePackage();
stageMainPackage(join(archivesDirectory, archive));

function stageMainPackage(archive) {
  const target = join(distDirectory, "bpmn-to-code");
  cpSync(join(packagesDirectory, "bpmn-to-code"), target, { recursive: true });
  copyJars(archive, join(target, "lib"));
  const manifest = readJson(join(target, "package.json"));
  manifest.version = version;
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

function copyJars(archive, target) {
  const unpacked = mkdtempSync(join(tmpdir(), "bpmn-to-code-"));
  execFileSync("unzip", ["-q", archive, "-d", unpacked]);
  const [rootFolder] = readdirSync(unpacked);
  cpSync(join(unpacked, rootFolder, "lib"), target, { recursive: true });
  rmSync(unpacked, { recursive: true, force: true });
}

function readJson(file) {
  return JSON.parse(readFileSync(file, "utf8"));
}

function writeJson(file, content) {
  writeFileSync(file, `${JSON.stringify(content, null, 2)}\n`);
}
