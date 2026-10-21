<#-- Wraps a rendered body: its imports are only known once the body has been rendered. -->
${generatedFileHeader}
package ${packageName};

<#list imports>
<#items as qualifiedName>
import ${qualifiedName};
</#items>

</#list>
${body}