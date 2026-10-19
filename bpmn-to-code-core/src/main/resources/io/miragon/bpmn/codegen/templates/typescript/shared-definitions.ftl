${generatedFileHeader}
<#if usesRuntime>

import type * as Runtime from "${runtimeModule}";
</#if>

/**
 * ${doc}
 */
export const ${typeName} = {
<#list constants as constant>
  ${constant.name}: ${constant.value},
</#list>
  get all(): readonly ${elementType}[] {
    return [
<#list constants as constant>
      ${typeName}.${constant.name},
</#list>
    ];
  },
} as const;
