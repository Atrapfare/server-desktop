<script setup>
import { computed, ref } from 'vue'
import { formatClock, useDashboard } from './composables/useDashboard.js'
import WeatherWidget from './components/WeatherWidget.vue'
import CalendarWidget from './components/CalendarWidget.vue'
import NewsWidget from './components/NewsWidget.vue'
import TransitWidget from './components/TransitWidget.vue'
import SystemWidget from './components/SystemWidget.vue'

const { widgets, loading, fetchError, connected, polling, summary, now, reload } = useDashboard()

const link = computed(() => {
  if (fetchError.value) return { key: 'down', text: 'Getrennt', title: `Backend nicht erreichbar: ${fetchError.value}` }
  if (connected.value) return { key: 'live', text: 'Live', title: 'Live-Verbindung steht, Werte kommen ungefragt an' }
  if (polling.value) return { key: 'poll', text: 'Abruf 60 s', title: 'Kein Stream — Abruf alle 60 Sekunden' }
  return { key: 'wait', text: 'Verbindet', title: 'Verbindung unterbrochen, Wiederaufbau läuft' }
})

const clock = computed(() => {
  const date = new Date(now.value)
  const pad = (value) => String(value).padStart(2, '0')
  return { hm: `${pad(date.getHours())}:${pad(date.getMinutes())}`, s: pad(date.getSeconds()) }
})

const today = computed(() => new Date(now.value).toLocaleDateString('de-DE', {
  weekday: 'long',
  day: 'numeric',
  month: 'long'
}))

const greeting = computed(() => {
  const hour = new Date(now.value).getHours()
  if (hour < 5) return 'Gute Nacht'
  if (hour < 11) return 'Guten Morgen'
  if (hour < 18) return 'Guten Tag'
  return 'Guten Abend'
})

// Der Knopf dreht sich einmal ganz durch, auch wenn die Antwort schneller da
// ist - ein auf halbem Weg abgebrochener Dreh wirkt wie ein Fehler.
const spinning = ref(false)

function refresh() {
  spinning.value = true
  setTimeout(() => { spinning.value = false }, 700)
  reload()
}
</script>

<template>
  <div class="shell">
    <header class="console">
      <div class="brand">
        <svg class="brand__mark" viewBox="0 0 64 64" aria-hidden="true">
          <defs>
            <linearGradient id="mark" x1="10" y1="54" x2="54" y2="14" gradientUnits="userSpaceOnUse">
              <stop offset="0" stop-color="#2f7fe0" />
              <stop offset="1" stop-color="#4fe3d2" />
            </linearGradient>
          </defs>
          <rect
            x="0.75" y="0.75" width="62.5" height="62.5" rx="14.25"
            fill="none" stroke="currentColor" stroke-opacity="0.25" stroke-width="1.5"
          />
          <g fill="url(#mark)">
            <rect x="13" y="33" width="8" height="18" rx="4" />
            <rect x="28" y="23" width="8" height="28" rx="4" />
            <rect x="43" y="29" width="8" height="22" rx="4" />
          </g>
          <circle cx="47" cy="16" r="5" fill="#f5b544" />
        </svg>

        <div class="brand__text">
          <p class="brand__kicker label">{{ today }}</p>
          <h1 class="brand__title">{{ greeting }}</h1>
        </div>
      </div>

      <p class="clock num">
        <span class="clock__hm">{{ clock.hm }}</span><span class="clock__s">{{ clock.s }}</span>
      </p>

      <div class="console__status">
        <div class="chips">
          <span v-if="fetchError" class="chip chip--error" :title="fetchError">Backend nicht erreichbar</span>
          <span v-else-if="loading" class="chip">lädt …</span>
          <template v-else>
            <span class="chip chip--ok"><i></i>{{ summary.ok }} OK</span>
            <span v-if="summary.stale" class="chip chip--stale"><i></i>{{ summary.stale }} veraltet</span>
            <span v-if="summary.error" class="chip chip--error"><i></i>{{ summary.error }} Fehler</span>
            <span class="stand num">Stand {{ formatClock(summary.newest) }}</span>
          </template>
        </div>

        <div class="console__tools">
          <span class="link" :class="`link--${link.key}`" :title="link.title">
            <span class="link__dot" aria-hidden="true"></span>{{ link.text }}
          </span>

          <button
            class="reload"
            type="button"
            :class="{ 'reload--spin': spinning }"
            title="Jetzt neu laden"
            @click="refresh"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
              <path d="M20 11a8 8 0 1 0-.6 4" />
              <path d="M20 4v7h-7" />
            </svg>
            <span class="sr-only">Neu laden</span>
          </button>
        </div>
      </div>
    </header>

    <main class="grid">
      <TransitWidget :payload="widgets.transit" />
      <WeatherWidget :payload="widgets.weather" />
      <CalendarWidget :payload="widgets.calendar" />
      <NewsWidget :payload="widgets.news" />
      <SystemWidget title="Heimserver" place="local" :payload="widgets.host" />
      <SystemWidget title="VPS" place="remote" :payload="widgets.vps" />
    </main>
  </div>
</template>

<style scoped>
.shell {
  max-width: 2200px;
  margin: 0 auto;
  padding: clamp(1.1rem, 2.2vw, 2rem) clamp(1rem, 2.2vw, 2.25rem) 3rem;
  display: flex;
  flex-direction: column;
  gap: clamp(1.1rem, 1.8vw, 1.75rem);
}

.console {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  column-gap: 1.5rem;
  row-gap: 0.9rem;
  animation: drop 0.7s var(--ease) both;
}

.brand {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  min-width: 0;
}

.brand__mark {
  width: 42px;
  height: 42px;
  flex: none;
  color: var(--text);
  filter: drop-shadow(0 4px 12px rgba(79, 157, 255, 0.22));
}

.brand__text {
  min-width: 0;
}

.brand__kicker {
  margin: 0 0 0.15rem;
  font-size: 0.66rem;
}

.brand__title {
  margin: 0;
  font-size: clamp(1.25rem, 2vw, 1.7rem);
  font-weight: 600;
  line-height: 1.1;
}

.clock {
  margin: 0;
  display: flex;
  align-items: baseline;
  gap: 0.2rem;
  line-height: 1;
}

.clock__hm {
  font-size: clamp(1.9rem, 4.4vw, 2.9rem);
  font-weight: 400;
  letter-spacing: -0.045em;
  background: linear-gradient(180deg, var(--text), #9fb3cc);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.clock__s {
  font-size: clamp(0.8rem, 1.2vw, 1rem);
  color: var(--accent);
  opacity: 0.75;
}

/* Die Statuszeile sitzt als eigene Leiste unter Marke und Uhr und traegt die
   Trennlinie zum Kachelraster. */
.console__status {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.5rem 0.9rem;
  padding-top: 0.85rem;
  border-top: 1px solid var(--line-faint);
}

.chips {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.4rem 0.55rem;
  min-width: 0;
}

.console__tools {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 0.55rem;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-family: var(--font-mono);
  font-size: 0.7rem;
  padding: 0.2rem 0.6rem;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: color-mix(in srgb, var(--surface) 70%, transparent);
  color: var(--text-muted);
  white-space: nowrap;
}

.chip i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.chip--ok { color: var(--ok); border-color: color-mix(in srgb, var(--ok) 28%, transparent); }
.chip--stale { color: var(--stale); border-color: color-mix(in srgb, var(--stale) 28%, transparent); }
.chip--error { color: var(--error); border-color: color-mix(in srgb, var(--error) 28%, transparent); }

.stand {
  font-size: 0.7rem;
  color: var(--text-faint);
}

.link {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  font-family: var(--font-mono);
  font-size: 0.68rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--text-muted);
  cursor: default;
}

.link__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-faint);
}

.link--live .link__dot {
  background: var(--ok);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--ok) 16%, transparent);
  animation: breathe 2.8s var(--ease) infinite;
}

.link--poll .link__dot { background: var(--stale); }
.link--wait .link__dot { background: var(--stale); animation: breathe 1.1s linear infinite; }
.link--down .link__dot { background: var(--error); }

.reload {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: 1px solid var(--line);
  background: var(--surface);
  color: var(--text-muted);
  cursor: pointer;
  transition: color 0.25s var(--ease), border-color 0.25s var(--ease), background 0.25s var(--ease);
}

.reload svg {
  width: 14px;
  height: 14px;
}

.reload:hover {
  color: var(--text);
  border-color: var(--line-strong);
  background: var(--surface-raised);
}

.reload--spin svg {
  animation: spin 0.7s var(--ease);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}

/* auto-fit + minmax traegt den Bereich von 360 bis 2560 px ohne Breakpoints:
   schmal einspaltig, breit so viele Spalten wie bei 340 px Mindestbreite passen.
   min() haelt die Mindestbreite auf sehr schmalen Geraeten im Rahmen. */
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(340px, 100%), 1fr));
  gap: var(--gap);
  /* Gleiche Hoehe je Zeile: die Kacheln bilden dann durchgehende Baender wie
     bei einer Instrumententafel, statt am unteren Rand auszufransen. */
  align-items: stretch;
}

.grid > * {
  animation: rise 0.75s var(--ease) both;
}

.grid > *:nth-child(1) { animation-delay: 0.06s; }
.grid > *:nth-child(2) { animation-delay: 0.12s; }
.grid > *:nth-child(3) { animation-delay: 0.18s; }
.grid > *:nth-child(4) { animation-delay: 0.24s; }
.grid > *:nth-child(5) { animation-delay: 0.3s; }
.grid > *:nth-child(6) { animation-delay: 0.36s; }

@keyframes rise {
  from { opacity: 0; transform: translateY(14px) scale(0.985); }
  to { opacity: 1; transform: none; }
}

@keyframes drop {
  from { opacity: 0; transform: translateY(-10px); }
  to { opacity: 1; transform: none; }
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@keyframes breathe {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.35; }
}

@media (max-width: 560px) {
  .console {
    grid-template-columns: 1fr;
  }
}
</style>
