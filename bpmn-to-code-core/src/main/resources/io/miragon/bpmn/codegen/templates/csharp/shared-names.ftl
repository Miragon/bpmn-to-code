<#include "file-header.ftl">

/// <summary>
/// ${doc}
/// </summary>
public static class ${typeName}
{
<#list constants as constant>
    public const string ${constant.name} = ${constant.value};
</#list>

    public static System.Collections.Generic.IReadOnlyList<string> All => new string[]
    {
<#list constants as constant>
        ${constant.name},
</#list>
    };
}
