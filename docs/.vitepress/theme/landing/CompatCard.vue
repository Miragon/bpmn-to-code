<script setup lang="ts">
import LucideIcon from './LucideIcon.vue'
</script>

<template>
  <dl class="spec">
    <div class="spec-row">
      <dt>Engines</dt>
      <dd>
        <span>Zeebe <span class="note">camunda 8</span></span>
        <span>Camunda 7</span>
        <span>Operaton</span>
        <span>CIB seven <span class="note">via camunda 7</span></span>
      </dd>
    </div>
    <div class="spec-row">
      <dt>Languages</dt>
      <dd>
        <span>Kotlin</span>
        <span>Java</span>
        <span>C#</span>
      </dd>
    </div>
    <div class="spec-row">
      <dt>Build</dt>
      <dd>
        <a href="https://plugins.gradle.org/plugin/io.miragon.bpmn-to-code-gradle" target="_blank" rel="noopener">Gradle<LucideIcon name="arrow-up-right" /></a>
        <a href="https://central.sonatype.com/artifact/io.miragon/bpmn-to-code-maven" target="_blank" rel="noopener">Maven<LucideIcon name="arrow-up-right" /></a>
        <a href="https://www.nuget.org/packages/Miragon.BpmnToCode.MSBuild" target="_blank" rel="noopener">MSBuild<LucideIcon name="arrow-up-right" /></a>
      </dd>
    </div>
  </dl>
</template>
