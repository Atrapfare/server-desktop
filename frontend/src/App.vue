<script setup>
import { useDashboard } from './composables/useDashboard.js'
import WeatherWidget from './components/WeatherWidget.vue'

const { widgets } = useDashboard()
</script>

<template>
  <main class="shell">
    <div class="grid">
      <WeatherWidget :payload="widgets.weather" />
    </div>
  </main>
</template>

<style scoped>
.shell {
  padding: 1.5rem;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: var(--gap);
}
</style>
