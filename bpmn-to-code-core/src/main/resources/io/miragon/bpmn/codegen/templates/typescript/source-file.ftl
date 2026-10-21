<#-- Wraps a rendered body: its imports are only known once the body has been rendered. -->
${generatedFileHeader}
<#switch runtimeImport>
<#case "TYPE">
import type * as Runtime from "@miragon/bpmn-to-code-runtime";
<#break>
<#case "VALUE">
import * as Runtime from "@miragon/bpmn-to-code-runtime";
</#switch>
<#list sharedDefinitions as typeName>
import { ${typeName} } from "./${typeName}.js";
</#list>

${body}