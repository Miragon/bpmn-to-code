<script setup lang="ts">
import LucideIcon from './LucideIcon.vue'
import type { RunnerRule } from './data'

defineProps<{
  command: string
  fail: RunnerRule[]
  failSummary: string
  failNote: string
  pass: RunnerRule[]
  passSummary: string
  passNote: string
}>()
</script>

<template>
  <div class="runner-wrap">
    <div class="runner bad">
      <div class="runner-titlebar">
        <span class="cmd">{{ command }}</span>
        <span class="status-badge bad"><LucideIcon name="x" />FAILED</span>
      </div>
      <div class="runner-body">
        <template v-for="rule in fail" :key="rule.name">
          <span class="rline err"><LucideIcon name="x" />{{ rule.name }}</span>
          <span class="rline hint" v-html="rule.hint"></span>
        </template>
      </div>
      <div class="runner-summary">
        <span>{{ failSummary }}</span>
        <span>{{ failNote }}</span>
      </div>
    </div>

    <span class="between"><LucideIcon name="arrow-right" /></span>

    <div class="runner good">
      <div class="runner-titlebar">
        <span class="cmd">{{ command }}</span>
        <span class="status-badge good"><LucideIcon name="check" />PASSED</span>
      </div>
      <div class="runner-body">
        <template v-for="rule in pass" :key="rule.name">
          <span class="rline ok"><LucideIcon name="check" />{{ rule.name }}</span>
          <span class="rline hint" v-html="rule.hint"></span>
        </template>
      </div>
      <div class="runner-summary">
        <span>{{ passSummary }}</span>
        <span>{{ passNote }}</span>
      </div>
    </div>
  </div>
</template>
