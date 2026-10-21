import { execFileSync } from "node:child_process";
import { existsSync, rmSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const testDirectory = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = join(testDirectory, "..", "..", "..", "..");
const sharedModels = join(repositoryRoot, "shared", "bpmn");
const coreModels = join(repositoryRoot, "bpmn-to-code-core", "src", "test", "resources", "bpmn");
const generatedDirectory = join(testDirectory, "generated");

const cases = [
  { name: "zeebe-bike-leasing", baseDir: join(sharedModels, "zeebe"), filePattern: "bike-leasing.bpmn", engine: "ZEEBE" },
  { name: "c7-bike-leasing", baseDir: join(sharedModels, "c7"), filePattern: "bike-leasing.bpmn", engine: "CAMUNDA_7" },
  { name: "operaton-bike-leasing", baseDir: join(sharedModels, "operaton"), filePattern: "bike-leasing.bpmn", engine: "OPERATON" },
  { name: "zeebe-membership", baseDir: join(sharedModels, "zeebe"), filePattern: "membership.bpmn", engine: "ZEEBE" },
  { name: "nested-subprocess", baseDir: coreModels, filePattern: "nested-subprocess.bpmn", engine: "ZEEBE" },
  {
    name: "bike-leasing-and-cancel-bike-order",
    baseDir: join(sharedModels, "zeebe"),
    filePattern: "{bike-leasing,cancel-bike-order}.bpmn",
    engine: "ZEEBE",
  },
];

const cli = cliPath();
rmSync(generatedDirectory, { recursive: true, force: true });
for (const { name, baseDir, filePattern, engine } of cases) {
  execFileSync(cli, [
    "generate-api",
    "--baseDir", baseDir,
    "--filePattern", filePattern,
    "--processEngine", engine,
    "--outputLanguage", "TYPESCRIPT",
    "--outputFolderPath", join(generatedDirectory, name),
    "--packagePath", "process",
  ], { stdio: "inherit", shell: process.platform === "win32" });
}

function cliPath() {
  const launcher = process.platform === "win32" ? "bpmn-to-code.bat" : "bpmn-to-code";
  const built = join(repositoryRoot, "bpmn-to-code-cli", "build", "install", "bpmn-to-code", "bin", launcher);
  const path = process.env.BPMN_TO_CODE_CLI ?? built;
  if (!existsSync(path)) {
    console.error(`No CLI at ${path}. Run ./gradlew :bpmn-to-code-cli:installDist or set BPMN_TO_CODE_CLI.`);
    process.exit(1);
  }
  return path;
}
