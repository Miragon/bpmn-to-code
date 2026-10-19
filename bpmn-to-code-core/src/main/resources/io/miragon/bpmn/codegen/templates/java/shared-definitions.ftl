/**
<#list javadoc as line>
 * ${line}
</#list>
 */
public final class ${typeName} {
<#list constants as constant>
  public static final ${constant.type} ${constant.name} = ${constant.value};

</#list>
  private ${typeName}() {
  }

  public static ${t.List}<${elementType}> all() {
    return ${t.List}.of(
<#list constants as constant>
        ${constant.name}<#if constant?has_next>,<#else>);</#if>
</#list>
  }
}
