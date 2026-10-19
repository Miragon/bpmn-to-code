#!/usr/bin/env bash
# Packs the NuGet packages into a local feed and builds a project that uses them the way a consumer does: the MSBuild
# package generates the Process APIs of the shared Zeebe models through the tool package during `dotnet build`, the
# project walks one with ProcessPath, and the tool validates the models through `dotnet tool exec`.
#
#   packages/nuget/smoke-test.sh <rid> <tool distribution> [version]
#
# The tool distribution is the output of `./gradlew :bpmn-to-code-cli:toolDistribution` on a machine of <rid>, the
# version defaults to a unique prerelease, so no package from an earlier run is taken from the NuGet cache. Needs the
# .NET 10 SDK and, for the Native AOT starter, the platform's C toolchain.
set -euo pipefail

if [ "$#" -lt 2 ]; then
  echo "usage: $0 <rid> <tool distribution> [version]" >&2
  exit 2
fi

rid="$1"
distribution="$(cd "$2" && pwd)"
version="${3:-0.0.0-smoke.$(date +%s)}"
nuget="$(cd "$(dirname "$0")" && pwd)"
models="$(cd "$nuget/../../shared/bpmn/zeebe" && pwd)"
work="$(mktemp -d)"
feed="$work/feed"
trap 'rm -rf "$work"' EXIT

pack() {
  dotnet pack "$nuget/src/$1" -c Release -o "$feed" -p:Version="$version" -nologo -v quiet "${@:2}"
}

pack Miragon.BpmnToCode.Runtime
pack Miragon.BpmnToCode.MSBuild
pack Miragon.BpmnToCode.Cli
pack Miragon.BpmnToCode.Cli -r "$rid" -p:BpmnToCodeDistribution="$distribution"
ls "$feed"

mkdir "$work/consumer"
cd "$work/consumer"
cat > nuget.config <<NUGET
<?xml version="1.0" encoding="utf-8"?>
<configuration>
  <packageSources>
    <clear />
    <add key="local" value="$feed" />
    <add key="nuget.org" value="https://api.nuget.org/v3/index.json" />
  </packageSources>
</configuration>
NUGET
cat > Consumer.csproj <<PROJECT
<Project Sdk="Microsoft.NET.Sdk">
  <PropertyGroup>
    <OutputType>Exe</OutputType>
    <TargetFramework>net10.0</TargetFramework>
    <Nullable>enable</Nullable>
    <TreatWarningsAsErrors>true</TreatWarningsAsErrors>
    <RootNamespace>MiraVelo.Process</RootNamespace>
    <BpmnToCodeProcessEngine>ZEEBE</BpmnToCodeProcessEngine>
    <BpmnToCodeBaseDir>$models</BpmnToCodeBaseDir>
    <BpmnToCodeFilePattern>*.bpmn</BpmnToCodeFilePattern>
  </PropertyGroup>
  <ItemGroup>
    <PackageReference Include="Miragon.BpmnToCode.MSBuild" Version="$version" />
  </ItemGroup>
</Project>
PROJECT
cat > Program.cs <<'PROGRAM'
using System;
using Miragon.BpmnToCode.Runtime.Path;
using MiraVelo.Process;

var path = ProcessPath.From(BikeLeasingProcessApi.FlowNodes.StartEventLeasingRequestReceived.Instance)
    .Then(next => next.ServiceTaskValidateApplication)
    .Then(next => next.BusinessRuleTaskCheckCreditRating);
Console.WriteLine(string.Join(",", path.Ids));
PROGRAM

dotnet build -nologo -v minimal
output="$(dotnet run --no-build)"
if [ "$output" != "startEvent_leasingRequestReceived,serviceTask_validateApplication,businessRuleTask_checkCreditRating" ]; then
  echo "unexpected output of the consumer: $output" >&2
  exit 1
fi

dotnet build -nologo -v minimal -t:BpmnToCodeValidate
dotnet tool exec "Miragon.BpmnToCode.Cli@$version" --yes -- --version

echo "NuGet smoke test of $version on $rid passed"
