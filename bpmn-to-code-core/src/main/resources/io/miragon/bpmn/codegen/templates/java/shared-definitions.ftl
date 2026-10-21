/**
<#list doc as line>
 * ${line}
</#list>
 */
public final class ${typeName} {
<#list constants as constant>
<#switch shape>
<#case "PLAIN_NAME">
  public static final ${elementType} ${constant.name} = ${constant.value};
<#break>
<#case "TYPED_NAME">
  public static final ${elementType} ${constant.name} = new ${elementType}(${constant.value});
<#break>
<#case "NAME_AND_CODE">
  public static final ${elementType} ${constant.name} = new ${elementType}(${constant.value}, ${constant.code});
</#switch>

</#list>
  private ${typeName}() {
  }

  public static ${types.List}<${elementType}> all() {
    return ${types.List}.of(
<#list constants as constant>
<#if constant?has_next>
        ${constant.name},
<#else>
        ${constant.name});
</#if>
</#list>
  }
}
