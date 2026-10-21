/**
<#list doc as line>
 * ${line}
</#list>
 */
object ${typeName} {
<#list constants as constant>
<#switch shape>
<#case "PLAIN_NAME">
  const val ${constant.name}: ${elementType} = ${constant.value}
<#break>
<#case "TYPED_NAME">
  val ${constant.name}: ${elementType} = ${elementType}(${constant.value})
<#break>
<#case "NAME_AND_CODE">
  val ${constant.name}: ${elementType} = ${elementType}(
    name = ${constant.value},
    code = ${constant.code},
  )
</#switch>
<#sep>

</#sep>
</#list>

  val all: ${types.List}<${elementType}> = listOf(
<#list constants as constant>
    ${constant.name},
</#list>
  )
}
