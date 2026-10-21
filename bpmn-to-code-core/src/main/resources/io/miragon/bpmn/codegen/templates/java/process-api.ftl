public final class ${typeName} {
  public static final ${types.ProcessId} PROCESS_ID = new ${types.ProcessId}(${processId});

  public static final ${types.BpmnEngine} PROCESS_ENGINE = ${types.BpmnEngine}.${processEngine};

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

    public static ${types.List}<${types.FlowNode}> all() {
      return ${types.List}.of(
<#list flowNodes as node>
<#if node?has_next>
          ${node.className}.INSTANCE,
<#else>
          ${node.className}.INSTANCE);
</#if>
</#list>
    }
<#list flowNodes as node>

<#if node.interfaces?has_content>
    public static final class ${node.className} extends ${types.AbstractFlowNode} implements ${node.interfaces?join(", ")} {
<#else>
    public static final class ${node.className} extends ${types.AbstractFlowNode} {
</#if>
      public static final ${types.String} ELEMENT_ID = ${node.elementId};

      public static final ${node.className} INSTANCE = new ${node.className}();
<#if node.variables?has_content>

      private static final Variables VARIABLES = new Variables();
</#if>

      private ${node.className}() {
<#if node.name??>
        super(new ${types.ElementId}(ELEMENT_ID), ${types.BpmnElementType}.${node.elementType}, ${node.name});
<#else>
        super(new ${types.ElementId}(ELEMENT_ID), ${types.BpmnElementType}.${node.elementType});
</#if>
      }
<#if node.eventType??>
<@getter type=types.BpmnEventType name="getEventType" value="${types.BpmnEventType}.${node.eventType}"/>
</#if>
<#if node.jobType??>
<@getter type=types.String name="getJobType" value=node.jobType/>
</#if>
<#if node.calledProcess??>
<@getter type=types.ProcessId name="getCalledProcess" value="new ${types.ProcessId}(${node.calledProcess})"/>
</#if>
<#if node.timer??>
<@getter type=types.BpmnTimer name="getTimer" value="new ${types.BpmnTimer}(${types.TimerType}.${node.timer.type}, ${node.timer.expression})"/>
</#if>
<#if node.message??>
<@sharedNameGetter type=types.MessageName name="getMessage" definition=node.message/>
</#if>
<#if node.signal??>
<@sharedNameGetter type=types.SignalName name="getSignal" definition=node.signal/>
</#if>
<#if node.error??>
<@sharedNameAndCodeGetter type=types.BpmnErrorDefinition name="getError" definition=node.error/>
</#if>
<#if node.escalation??>
<@sharedNameAndCodeGetter type=types.BpmnEscalationDefinition name="getEscalation" definition=node.escalation/>
</#if>
<#if node.attachedTo??>
<@getter type=node.attachedTo name="getAttachedTo" value="${node.attachedTo}.INSTANCE"/>
</#if>
<#if node.interrupting??>
<@getter type="boolean" name="isInterrupting" value=node.interrupting?c overrides=node.attachedTo??/>
</#if>
<#if node.variables?has_content>
<@getter type="Variables" name="getVariables" value="VARIABLES"/>
</#if>
<#if node.successors?has_content>
<@getter type="Next" name="getNext" value="new Next()"/>
</#if>
<#if node.interiorStarts?has_content>
<@getter type="Start" name="getStartEvents" value="new Start()"/>
</#if>
<#list node.variables>

      public static final class Variables extends ${types.VariableDefinitions} {
<#items as variable>
        public static final ${types.VariableName}.${variable.subtype} ${variable.name} = new ${types.VariableName}.${variable.subtype}(${variable.nameConstant});

</#items>
        private Variables() {
        }

        @${types.Override}
        public ${types.List}<${types.VariableName}> getAll() {
          return ${types.List}.of(${node.variables?map(variable -> variable.name)?join(", ")});
        }
      }
</#list>
<@mappings className="Inputs" mappings=node.inputs/>
<@mappings className="Outputs" mappings=node.outputs/>
<#list node.successors>

      public static final class Next {
<#items as successor>
        public ${successor.type}<${successor.target}> ${successor.name}() {
<#if successor.flows?size == 1>
          return ${types.SequenceFlows}.single(${flowArguments(successor.flows[0], successor.target)});
<#elseif successor.flows?has_content>
          return new ${types.SequenceFlows}<>(${successor.target}.INSTANCE, ${types.List}.of(${sequenceFlows(successor)}));
<#else>
          return new ${successor.type}<>(${successor.target}.INSTANCE);
</#if>
        }
<#sep>

</#sep>
</#items>
      }
</#list>
<#list node.interiorStarts>

      public static final class Start {
<#items as start>
        public ${start.className} ${start.name}() {
          return ${start.className}.INSTANCE;
        }
<#sep>

</#sep>
</#items>
      }
</#list>
    }
</#list>
  }
</#if>
}
<#macro getter type name value overrides=true>

<#if overrides>
      @${types.Override}
</#if>
      public ${type} ${name}() {
        return ${value};
      }
</#macro>
<#macro sharedNameGetter type name definition>
<#if definition.reference??>
<@getter type=type name=name value=definition.reference/>
<#else>
<@getter type=type name=name value="new ${type}(${definition.literal})"/>
</#if>
</#macro>
<#macro sharedNameAndCodeGetter type name definition>
<#if definition.reference??>
<@getter type=type name=name value=definition.reference/>
<#else>
<@getter type=type name=name value="new ${type}(${definition.name}, ${definition.code})"/>
</#if>
</#macro>
<#macro mappings className mappings>
<#list mappings>

      public static final class ${className} {
<#items as mapping>
        public static final ${types.InputOutputMapping} ${mapping.name} = new ${types.InputOutputMapping}(${mapping.target}, ${mapping.source}, ${mapping.sourceExpression});

</#items>
        private ${className}() {
        }
      }
</#list>
</#macro>
<#function flowArguments flow target>
  <#local id = "new ${types.ElementId}(${flow.id})">
  <#if flow.onlyDefaults>
    <#return "${id}, ${target}.INSTANCE">
  </#if>
  <#return "${id}, ${flow.name}, ${flow.conditionExpression}, ${flow.defaultFlow?c}, ${target}.INSTANCE">
</#function>
<#function sequenceFlows successor>
  <#local flows = successor.flows?map(flow -> "new ${types.SequenceFlow}<>(${flowArguments(flow, successor.target)})")>
  <#return flows?join(", ")>
</#function>
