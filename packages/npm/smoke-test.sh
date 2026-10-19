#!/usr/bin/env bash
# Packs the npm packages built by `./gradlew :bpmn-to-code-cli:npmPlatformPackage :bpmn-to-code-cli:npmMainPackage`
# and the runtime package, installs them into an empty project, runs bpmn-to-code-cli/scripts/smoke-test.sh through
# them, and generates every language from the shared models. The generated TypeScript is type-checked against the
# installed runtime package with the tsc pinned in tools/.
set -euo pipefail

repo="$(cd "$(dirname "$0")/../.." && pwd)"
npm_build="$repo/bpmn-to-code-cli/build/npm"
work="$(mktemp -d)"
models="$repo/shared/bpmn/zeebe"

mkdir "$work/tools" "$work/runtime"
for package in "$npm_build"/platform/*/ "$npm_build/bpmn-to-code/"; do
  (cd "$package" && npm pack --silent --pack-destination "$work/tools" >/dev/null)
done
(cd "$repo/packages/npm/bpmn-to-code-runtime" && npm ci --silent && npm run --silent build && npm pack --silent --pack-destination "$work/runtime" >/dev/null)

mkdir "$work/project"
cd "$work/project"
echo '{ "name": "smoke-test", "private": true, "type": "module" }' > package.json
npm install --silent --save-dev "$work"/tools/*.tgz
npm install --silent "$work"/runtime/*.tgz

"$repo/bpmn-to-code-cli/scripts/smoke-test.sh" npx --no-install bpmn-to-code

for language in KOTLIN JAVA CSHARP TYPESCRIPT; do
  package_path=()
  if [ "$language" != "TYPESCRIPT" ]; then package_path=(--packagePath io.miravelo.process); fi
  npx --no-install bpmn-to-code generate-api --baseDir "$models" --filePattern "*.bpmn" --processEngine ZEEBE \
    --outputLanguage "$language" ${package_path[@]+"${package_path[@]}"} --outputFolderPath "generated/$language"
done

cat > tsconfig.json <<'TSCONFIG'
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "NodeNext",
    "strict": true,
    "noEmit": true,
    "verbatimModuleSyntax": true,
    "erasableSyntaxOnly": true,
    "noUnusedLocals": true
  },
  "include": ["generated/TYPESCRIPT"]
}
TSCONFIG
"$repo/tools/node_modules/.bin/tsc" -p tsconfig.json

echo "npm smoke test passed: $(ls generated/TYPESCRIPT | wc -l | tr -d ' ') TypeScript files"
