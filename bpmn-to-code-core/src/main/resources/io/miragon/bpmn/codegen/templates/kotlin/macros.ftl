<#-- A named call, one argument per line, its closing parenthesis indented by `indent`. -->
<#macro call c indent>
${c.function}(
<#list c.arguments as argument>
<#if argument.calls??>
${indent}  ${argument.name} = listOf(
<#list argument.calls as nested>
${indent}    ${nested.function}(
<#list nested.arguments as nestedArgument>
${indent}      ${nestedArgument.name} = ${nestedArgument.value},
</#list>
${indent}    ),
</#list>
${indent}  ),
<#else>
${indent}  ${argument.name} = ${argument.value},
</#if>
</#list>
${indent})<#t>
</#macro>
<#-- A property at `indent`: `= value`, `= ` and a call, or a getter on the next line. -->
<#macro property p indent>
<#if p.getter??>
${indent}${p.modifiers}val ${p.name}: ${p.type}
<#if p.getter.call??>
${indent}  get() = <@call p.getter.call indent + "  "/>
<#else>
${indent}  get() = ${p.getter.value}
</#if>
<#elseif p.call??>
${indent}${p.modifiers}val ${p.name}: ${p.type} = <@call p.call indent/>
<#else>
${indent}${p.modifiers}val ${p.name}: ${p.type} = ${p.value}
</#if>
</#macro>
<#-- Properties at `indent`, separated by blank lines. -->
<#macro properties ps indent>
<#list ps as p>
<#if p?index gt 0>

</#if>
<@property p indent/>
</#list>
</#macro>
