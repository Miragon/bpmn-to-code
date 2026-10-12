<script setup lang="ts">
defineProps<{
  name: 'check' | 'x' | 'triangle-alert' | 'arrow-right' | 'workflow' | 'file-code' | 'braces' | 'arrow-up-right'
}>()
</script>

<template>
  <svg
    class="lucide"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="2.5"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <template v-if="name === 'check'"><path d="M20 6 9 17l-5-5" /></template>
    <template v-else-if="name === 'x'"><path d="M18 6 6 18" /><path d="m6 6 12 12" /></template>
    <template v-else-if="name === 'triangle-alert'">
      <path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3" />
      <path d="M12 9v4" />
      <path d="M12 17h.01" />
    </template>
    <template v-else-if="name === 'workflow'">
      <rect width="8" height="8" x="3" y="3" rx="2" />
      <path d="M7 11v4a2 2 0 0 0 2 2h4" />
      <rect width="8" height="8" x="13" y="13" rx="2" />
    </template>
    <template v-else-if="name === 'file-code'">
      <path d="M10 12.5 8 15l2 2.5" />
      <path d="m14 12.5 2 2.5-2 2.5" />
      <path d="M14 2v4a2 2 0 0 0 2 2h4" />
      <path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7z" />
    </template>
    <template v-else-if="name === 'braces'">
      <path d="M8 3H7a2 2 0 0 0-2 2v5a2 2 0 0 1-2 2 2 2 0 0 1 2 2v5c0 1.1.9 2 2 2h1" />
      <path d="M16 21h1a2 2 0 0 0 2-2v-5c0-1.1.9-2 2-2a2 2 0 0 1-2-2V5a2 2 0 0 0-2-2h-1" />
    </template>
    <template v-else-if="name === 'arrow-up-right'"><path d="M7 7h10v10" /><path d="M7 17 17 7" /></template>
    <template v-else><path d="M5 12h14" /><path d="m12 5 7 7-7 7" /></template>
  </svg>
</template>
