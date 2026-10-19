public final class ${typeName} {
  public static final ${t.ProcessId} PROCESS_ID = new ${t.ProcessId}(${processId});

  public static final ${t.BpmnEngine} PROCESS_ENGINE = ${t.BpmnEngine}.${processEngine};

  private ${typeName}() {
  }
<#if flowNodes??>

  /**
   * Typed navigation over the process flow: one nested class per BPMN element.
   */
  public static final class FlowNodes {
    private FlowNodes() {
    }
<#list flowNodes as node>

    public static ${node.className} ${node.accessorName}() {
      return ${node.className}.INSTANCE;
    }
</#list>

    public static ${t.List}<${t.FlowNode}> all() {
      return ${t.List}.of(
<#list flowNodes as node>
          ${node.className}.INSTANCE<#if node?has_next>,<#else>);</#if>
</#list>
    }
<#list flowNodes as node>

    public static final class ${node.className} extends ${t.AbstractFlowNode}<#if node.interfaces?has_content> implements ${node.interfaces}</#if> {
      public static final ${t.String} ELEMENT_ID = ${node.elementId};

      public static final ${node.className} INSTANCE = new ${node.className}();
<#if node.variables??>

      private static final Variables VARIABLES = new Variables();
</#if>

      private ${node.className}() {
        super(new ${t.ElementId}(ELEMENT_ID), ${node.elementType}<#if node.name??>, ${node.name}</#if>);
      }
<#list node.getters as getter>

<#if getter.overrides>
      @${t.Override}
</#if>
      public ${getter.type} ${getter.name}() {
        return ${getter.value};
      }
</#list>
<#if node.successors?has_content>

      @${t.Override}
      public Next getNext() {
        return new Next();
      }
</#if>
<#if node.interiorStarts?has_content>

      @${t.Override}
      public Start getStartEvents() {
        return new Start();
      }
</#if>
<#if node.variables??>

      public static final class Variables extends ${t.VariableDefinitions} {
<#list node.variables.constants as constant>
        public static final ${constant.type} ${constant.name} = ${constant.value};

</#list>
        private Variables() {
        }

        @${t.Override}
        public ${t.List}<${t.VariableName}> getAll() {
          return ${t.List}.of(${node.variables.all});
        }
      }
</#if>
<#if node.inputs?has_content>

      public static final class Inputs {
<#list node.inputs as constant>
        public static final ${constant.type} ${constant.name} = ${constant.value};

</#list>
        private Inputs() {
        }
      }
</#if>
<#if node.outputs?has_content>

      public static final class Outputs {
<#list node.outputs as constant>
        public static final ${constant.type} ${constant.name} = ${constant.value};

</#list>
        private Outputs() {
        }
      }
</#if>
<#if node.successors?has_content>

      public static final class Next {
<#list node.successors as successor>
<#if successor?index gt 0>

</#if>
        public ${successor.type} ${successor.name}() {
          return ${successor.value};
        }
</#list>
      }
</#if>
<#if node.interiorStarts?has_content>

      public static final class Start {
<#list node.interiorStarts as start>
<#if start?index gt 0>

</#if>
        public ${start.type} ${start.name}() {
          return ${start.value};
        }
</#list>
      }
</#if>
    }
</#list>
  }
</#if>
}
