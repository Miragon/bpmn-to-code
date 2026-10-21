/**
 * ${doc}
 */
export const ${typeName} = {
<#list constants as constant>
<#if constant.code??>
  ${constant.key}: { name: ${constant.value}, code: ${constant.code} },
<#else>
  ${constant.key}: ${constant.value},
</#if>
</#list>
  get all(): readonly ${elementType}[] {
    return [
<#list constants as constant>
      ${typeName}.${constant.key},
</#list>
    ];
  },
} as const;
