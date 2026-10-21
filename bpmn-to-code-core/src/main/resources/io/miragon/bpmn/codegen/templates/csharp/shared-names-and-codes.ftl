<#include "file-header.ftl">

/// <summary>
/// ${doc}
/// </summary>
public static class ${typeName}
{
<#list definitions as definition>
    public static class ${definition.className}
    {
        public const string ${definition.referenceName} = ${definition.reference};
        public const string ${definition.codeName} = ${definition.code};
    }
<#sep>

</#sep>
</#list>

    public static System.Collections.Generic.IReadOnlyList<(string Reference, string Code)> All => new (string Reference, string Code)[]
    {
<#list definitions as definition>
        (${definition.className}.${definition.referenceName}, ${definition.className}.${definition.codeName}),
</#list>
    };
}
