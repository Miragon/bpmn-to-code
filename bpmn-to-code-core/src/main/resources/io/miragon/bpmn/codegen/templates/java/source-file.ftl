${generatedFileHeader}
package ${packageName};

<#list imports as qualifiedName>
import ${qualifiedName};
</#list>
<#if imports?has_content>

</#if>
${body}