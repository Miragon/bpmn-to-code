<#import "macros.ftl" as kt>
/**
<#list kdoc as line>
 * ${line}
</#list>
 */
object ${typeName} {
<@kt.properties constants "  "/>

  val all: ${t.List}<${elementType}> = listOf(
<#list constants as constant>
    ${constant.name},
</#list>
  )
}
