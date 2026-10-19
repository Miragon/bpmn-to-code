<#function object entries>
<#local lines = []>
<#list entries as entry>
<#local lines = lines + ["    ${entry.name}: ${entry.value},"]>
</#list>
<#return lines?join("\n")>
</#function>
${generatedFileHeader}
<#if flowNodes??>

import * as Runtime from "${runtimeModule}";
</#if>
<#list sharedImports as shared>
import { ${shared} } from "./${shared}.js";
</#list>
<#if flowNodes??>
<#list flowNodes as node>

class ${node.className} implements ${node.interfaces} {
  static readonly INSTANCE = new ${node.className}();

  private constructor() {}

  readonly ELEMENT_ID = ${node.elementId};
  readonly id = ${node.elementId};
  readonly elementType = ${node.elementType};
<#if node.eventType??>
  readonly eventType = ${node.eventType};
</#if>
  readonly name = ${node.name};
<#list node.fields as field>
  readonly ${field.name}<#if field.type??>: ${field.type}</#if> = ${field.value};
</#list>
<#if node.variables?has_content>
  readonly variables = Runtime.variableDefinitions({
${object(node.variables)}
  });
</#if>
<#if node.inputs?has_content>
  readonly Inputs = {
${object(node.inputs)}
  } as const satisfies Record<string, Runtime.InputOutputMapping>;
</#if>
<#if node.outputs?has_content>
  readonly Outputs = {
${object(node.outputs)}
  } as const satisfies Record<string, Runtime.InputOutputMapping>;
</#if>
<#if node.attachedTo??>

  get attachedTo(): ${node.attachedTo} {
    return ${node.attachedTo}.INSTANCE;
  }
</#if>
<#if node.successors?has_content>

  get next() {
    return {
<#list node.successors as successor>
      get ${successor.name}(): ${successor.type} {
        return ${successor.value};
      },
</#list>
    };
  }
</#if>
<#if node.interiorStarts?has_content>

  get startEvents() {
    return {
<#list node.interiorStarts as start>
      get ${start.name}(): ${start.type} {
        return ${start.value};
      },
</#list>
    };
  }
</#if>
}
</#list>
</#if>

export const ${typeName} = {
  PROCESS_ID: ${processId},
  PROCESS_ENGINE: ${processEngine},
<#if flowNodes??>
  FlowNodes: {
<#list flowNodes as node>
    ${node.className}: ${node.className}.INSTANCE,
</#list>
    all: [
<#list flowNodes as node>
      ${node.className}.INSTANCE,
</#list>
    ] as readonly Runtime.FlowNode[],
  },
</#if>
} as const;
