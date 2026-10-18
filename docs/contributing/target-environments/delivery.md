# Delivery Channels

How a delivery channel works, and which way of packaging is still open.

## How a package manager delivers a command-line tool

A package from npm, NuGet or PyPI does not have to be a library that code imports. It can also carry a program and make it available as a command. This is common, and it is how tools reach a project that are not written in that project's language.

| Tool | Written in | Delivered through | How |
|---|---|---|---|
| [esbuild](https://esbuild.github.io/getting-started/) | Go | npm | One npm package per operating system holds the program, the main package starts it |
| [Biome](https://biomejs.dev/guides/manual-installation/) | Rust | npm | Same pattern |
| [Ruff](https://docs.astral.sh/ruff/installation/) | Rust | PyPI | The Python package contains the program |
| [OpenAPI Generator](https://openapi-generator.tech/docs/installation/) | Java | npm, PyPI, Homebrew | A wrapper package starts the Java program and expects Java to be installed |
| any [.NET tool](https://learn.microsoft.com/en-us/dotnet/core/tools/global-tools) | C# | NuGet | `dotnet tool install` puts a command on the path |

For npm the mechanics are three small pieces.

![npm run generate calls launcher.js from the bpmn-to-code npm package; the launcher starts the command-line tool with its Java runtime from the package for the current operating system; the tool reads the BPMN files and writes TypeScript files into the project](../images/target-environments-npm.svg)

**1. The package declares a command.** The [`bin` field](https://docs.npmjs.com/cli/v11/configuring-npm/package-json#bin) of a `package.json` maps a command name to a file. After `npm install`, every script of the project can call that command.

```json
{
  "name": "bpmn-to-code",
  "bin": { "bpmn-to-code": "bin/launcher.js" },
  "optionalDependencies": {
    "@bpmn-to-code/darwin-arm64": "6.3.0",
    "@bpmn-to-code/linux-x64": "6.3.0",
    "@bpmn-to-code/win32-x64": "6.3.0"
  }
}
```

**2. The program itself sits in one package per operating system.** Each of those packages states which system it is for, and npm installs only the one that fits. This is what the `optionalDependencies` above are for. What it holds for bpmn-to-code depends on [how Java travels along](#carrying-java-along).

**3. The launcher starts the program.** It is the only JavaScript involved, and it does nothing but pass the arguments on.

```js
#!/usr/bin/env node
const { spawnSync } = require("node:child_process");

const tool = require.resolve(`@bpmn-to-code/${process.platform}-${process.arch}/bin/bpmn-to-code`);
const result = spawnSync(tool, process.argv.slice(2), { stdio: "inherit" });
process.exit(result.status);
```

Package names and versions here are examples. The team that uses it sees none of this: it adds one dev dependency and one script, as shown on the [overview](./#what-a-team-would-see), and never learns that the tool is not written in JavaScript.

The same idea carries over to the other ecosystems. A NuGet package can be a `dotnet tool`, which gives a .NET project the same command; a PyPI package can contain the program the way Ruff does. That is what "delivery channel" means in a language package: a thin package of this kind, nothing more.

## Carrying Java along

Decided: **B1, bundle a Java runtime.** The command-line tool is a Java program, so each channel has to deal with the Java runtime. This only applies as long as [core stays Kotlin](./core-language).

| Option | What it means | Consequence |
|---|---|---|
| B1 Bundle a Java runtime | The package contains the tool and a trimmed runtime, one package per operating system and CPU | Nobody installs Java. Larger download, and a build per platform |
| B2 Require installed Java | The package contains only the tool and expects `java` on the path | Smallest package, one build. OpenAPI Generator does this. A .NET or frontend team has to install a JDK after all |
| B3 Native binary (GraalVM) | The tool is compiled ahead of time | Smallest and fastest. Unproven for the BPMN reader and its XML schema validation, and a build per platform |

A throwaway spike of B1 gave these numbers on one developer machine (Apple Silicon). The spike code was not kept.

| Measured | Result |
|---|---|
| Runtime modules needed | `java.base`, `java.xml`, `java.compiler`, `java.logging` |
| Size | 43 MB unpacked, 28 MB as archive |
| Generating five Zeebe models, including the start of the JVM | about 0.4 s |
| Output | byte-identical to what the build plugins write |

Start time is therefore no reason for B3.

Back to the [overview](./).
