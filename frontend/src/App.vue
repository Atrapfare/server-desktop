<script setup>
import { computed } from 'vue'
import { formatClock, useDashboard } from './composables/useDashboard.js'
import WeatherWidget from './components/WeatherWidget.vue'
import CalendarWidget from './components/CalendarWidget.vue'
import NewsWidget from './components/NewsWidget.vue'

const { widgets, loading, fetchError, connected, polling, summary, now } = useDashboard()

const linkClass = computed(() => {
  if (connected.value) return 'link--live'
  if (polling.value) return 'link--poll'
  return 'link--down'
})

const linkTitle = computed(() => {
  if (connected.value) return 'Live-Verbindung steht'
  if (polling.value) return 'Kein Stream — Abruf alle 60 Sekunden'
  return 'Verbindung unterbrochen, Wiederaufbau läuft'
})

const today = computed(() => new Date(now.value).toLocaleDateString('de-DE', {
  weekday: 'long',
  day: 'numeric',
  month: 'long'
}))
</script>

<template>
  <div class="shell">
    <header class="bar">
      <h1 class="bar__title">Dashboard</h1>
      <span class="bar__date">{{ today }}</span>

      <div class="bar__status">
        <span v-if="fetchError" class="chip chip--error" :title="fetchError">Backend nicht erreichbar</span>
        <template v-else-if="loading">
          <span class="chip">lädt …</span>
        </template>
        <template v-else>
          <span class="chip chip--ok">{{ summary.ok }} OK</span>
          <span v-if="summary.stale" class="chip chip--stale">{{ summary.stale }} veraltet</span>
          <span v-if="summary.error" class="chip chip--error">{{ summary.error }} Fehler</span>
          <span class="bar__time">Stand {{ formatClock(summary.newest) }}</span>
          <span class="link" :class="linkClass" :title="linkTitle" aria-hidden="true"></span>
        </template>
      </div>
    </header>

    <main class="grid">
      <WeatherWidget :payload="widgets.weather" />
      <CalendarWidget :payload="widgets.calendar" />
      <NewsWidget :payload="widgets.news" />
    </main>
  </div>
</template>

<style scoped>
.shell {
  max-width: 2200px;
  margin: 0 auto;
  padding: 1.25rem clamp(1rem, 2vw, 2rem) 2rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.bar {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 0.5rem 1rem;
  padding-bottom: 0.9rem;
  border-bottom: 1px solid var(--border);
}

.bar__title {
  margin: 0;
  font-size: 1.15rem;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.bar__date {
  color: var(--text-muted);
  font-size: 0.9rem;
}

.bar__status {
  margin-left: auto;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.4rem 0.6rem;
}

.bar__time {
  font-size: 0.8rem;
  color: var(--text-faint);
  font-variant-numeric: tabular-nums;
}

.chip {
  font-size: 0.75rem;
  letter-spacing: 0.03em;
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
  border: 1px solid var(--border);
  color: var(--text-muted);
  white-space: nowrap;
}

.chip--ok {
  color: var(--ok);
  border-color: color-mix(in srgb, var(--ok) 35%, transparent);
}

.chip--stale {
  color: var(--stale);
  border-color: color-mix(in srgb, var(--stale) 35%, transparent);
}

.chip--error {
  color: var(--error);
  border-color: color-mix(in srgb, var(--error) 35%, transparent);
}

.link {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: none;
}

.link--live { background: var(--ok); }
.link--poll { background: var(--stale); }
.link--down { background: var(--error); }

/* auto-fit + minmax traegt den Bereich von 800 bis 2560 px ohne Breakpoints:
   schmal einspaltig, breit so viele Spalten wie bei 340 px Mindestbreite passen. */
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(340px, 1fr));
  gap: var(--gap);
  align-items: start;
}
</style>
