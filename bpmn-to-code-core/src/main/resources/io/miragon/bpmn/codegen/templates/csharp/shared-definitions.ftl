<#include "header.ftl">
/// <summary>
/// ${doc}
/// </summary>
public static class ${typeName}
{
<#list constants as constant>
    public ${modifiers} ${elementType} ${constant.name} = ${constant.value};
</#list>

    public static System.Collections.Generic.IReadOnlyList<${elementType}> All => new ${elementType}[]
    {
<#list constants as constant>
        ${constant.name},
</#list>
    };
}
