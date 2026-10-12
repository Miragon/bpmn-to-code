<script setup lang="ts">
import LucideIcon from './LucideIcon.vue'

defineProps<{
  beforePill: string
  beforeTitle: string
  beforeCode: string
  beforeVariant?: 'bad' | 'warn'
  afterPill: string
  afterTitle: string
  afterCode: string
  scrollBefore?: boolean
}>()
</script>

<template>
  <div class="compare">
    <div class="compare-card" :class="beforeVariant ?? 'bad'">
      <div class="compare-head">
        <span class="status-badge" :class="beforeVariant ?? 'bad'">
          <LucideIcon :name="beforeVariant === 'warn' ? 'triangle-alert' : 'x'" />{{ beforePill }}
        </span>
        <span class="compare-title">{{ beforeTitle }}</span>
      </div>
      <div class="compare-code" :class="{ scroll: scrollBefore }" v-html="beforeCode"></div>
    </div>
    <span class="between"><LucideIcon name="arrow-right" /></span>
    <div class="compare-card good">
      <div class="compare-head">
        <span class="status-badge good"><LucideIcon name="check" />{{ afterPill }}</span>
        <span class="compare-title">{{ afterTitle }}</span>
      </div>
      <div class="compare-code" v-html="afterCode"></div>
    </div>
  </div>
</template>
