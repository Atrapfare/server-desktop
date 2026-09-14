<script setup>
import { computed } from 'vue'
import { formatClock, formatRelative, useDashboard } from '../composables/useDashboard.js'

const props = defineProps({
  title: { type: String, required: true },
  payload: { type: Object, default: null }
})

const { now } = useDashboard()

const status = computed(() => props.payload?.status ?? 'LOADING')
const key = computed(() => status.value.toLowerCase())
const isStale = computed(() => status.value === 'STALE')
const isError = computed(() => status.value === 'ERROR')
const hasData = computed(() => props.payload?.data != null)
const age = computed(() => formatRelative(props.payload?.lastUpdated, now.value))
const exactTime = computed(() => (props.payload?.lastUpdated ? `Letzter Abruf ${formatClock(props.payload.lastUpdated)}` : 'Noch kein Abruf'))
</script>

<template>
  <section class="card" :class="`card--${key}`">
    <span class="card__rail" aria-hidden="true"></span>

    <header class="card__head">
      <span class="card__glyph" aria-hidden="true"><slot name="icon" /></span>
      <h2 class="card__title">{{ title }}</h2>
      <span class="card__meta" :title="exactTime">
        <span class="beacon" aria-hidden="true"></span>
        <span class="card__age num">{{ age }}</span>
      </span>
    </header>

    <p v-if="isStale" class="notice notice--stale">
      Veraltet — letzter Abruf fehlgeschlagen<span v-if="payload.error">: {{ payload.error }}</span>
    </p>

    <div v-if="isError" class="notice notice--error">
      {{ payload.error || 'Keine Daten verfügbar.' }}
    </div>

    <div v-else-if="hasData" class="card__body" :class="{ 'card__body--stale': isStale }">
      <slot />
    </div>

    <div v-else class="card__body">
      <slot name="empty">
        <div class="skeleton"></div>
        <div class="skeleton skeleton--short"></div>
      </slot>
    </div>
  </section>
</template>

<style scoped>
.card {
  --tint: var(--text-faint);

  position: relative;
  isolation: isolate;
  overflow: hidden;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.035), rgba(255, 255, 255, 0) 46%),
    var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 1.1rem 1.25rem 1.3rem;
  display: flex;
  flex-direction: column;
  gap: 0.95rem;
  min-width: 0;
  box-shadow: var(--shadow);
  transition: border-color 0.35s var(--ease), transform 0.35s var(--ease), box-shadow 0.35s var(--ease);
}

.card--ok { --tint: var(--ok); }
.card--stale { --tint: var(--stale); }
.card--error { --tint: var(--error); }

/* Der Zustand liegt als Lichtstreifen auf der Oberkante statt als farbiger
   Rahmen - er ist im Augenwinkel erkennbar, ohne den Inhalt einzufaerben. */
.card__rail {
  position: absolute;
  inset: 0 0 auto;
  height: 1px;
  background: linear-gradient(90deg, var(--tint), color-mix(in srgb, var(--tint) 25%, transparent) 38%, transparent 72%);
  opacity: 0.85;
}

.card::before {
  content: '';
  position: absolute;
  top: -70px;
  left: -40px;
  width: 240px;
  height: 150px;
  z-index: -1;
  background: radial-gradient(closest-side, color-mix(in srgb, var(--tint) 16%, transparent), transparent);
  opacity: 0.55;
  pointer-events: none;
}

@media (hover: hover) {
  .card:hover {
    transform: translateY(-2px);
    border-color: var(--line-strong);
    box-shadow: 0 30px 56px -34px rgba(0, 0, 0, 1);
  }
}

.card__head {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.card__glyph {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  flex: none;
  border-radius: 8px;
  color: var(--text-muted);
  background: var(--surface-raised);
  border: 1px solid var(--line-faint);
}

.card__glyph :deep(svg) {
  width: 15px;
  height: 15px;
  display: block;
}

.card__title {
  margin: 0;
  font-family: var(--font-mono);
  font-size: 0.7rem;
  font-weight: 500;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.card__meta {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  white-space: nowrap;
  cursor: default;
}

.card__age {
  font-size: 0.72rem;
  color: var(--text-faint);
}

.beacon {
  position: relative;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex: none;
  background: var(--tint);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--tint) 14%, transparent);
}

/* Nur der gesunde Zustand pulst. Ein blinkendes Warnsignal zieht die
   Aufmerksamkeit dauerhaft auf sich, ein ruhendes rotes Licht reicht. */
.card--ok .beacon::after {
  content: '';
  position: absolute;
  inset: -3px;
  border-radius: 50%;
  border: 1px solid var(--tint);
  animation: ping 2.6s var(--ease) infinite;
}

@keyframes ping {
  0% { transform: scale(0.8); opacity: 0.7; }
  70%, 100% { transform: scale(2.2); opacity: 0; }
}

.card__body {
  min-width: 0;
  transition: opacity 0.4s var(--ease);
}

/* Veraltete Werte bleiben lesbar, treten aber sichtbar zurueck. */
.card__body--stale {
  opacity: 0.58;
}

.notice {
  margin: 0;
  font-size: 0.8rem;
  line-height: 1.45;
  border-radius: var(--radius-sm);
  padding: 0.55rem 0.7rem;
  overflow-wrap: anywhere;
  border: 1px solid transparent;
}

.notice--stale {
  color: var(--stale);
  background: color-mix(in srgb, var(--stale) 10%, transparent);
  border-color: color-mix(in srgb, var(--stale) 22%, transparent);
}

.notice--error {
  color: color-mix(in srgb, var(--error) 85%, white);
  background: color-mix(in srgb, var(--error) 10%, transparent);
  border-color: color-mix(in srgb, var(--error) 22%, transparent);
}

.skeleton {
  height: 1.5rem;
  border-radius: var(--radius-sm);
  background: linear-gradient(90deg, var(--surface-raised) 8%, var(--line) 28%, var(--surface-raised) 48%);
  background-size: 260% 100%;
  animation: shimmer 1.6s linear infinite;
  margin-bottom: 0.55rem;
}

.skeleton--short {
  width: 52%;
  margin-bottom: 0;
}

@keyframes shimmer {
  from { background-position: 160% 0; }
  to { background-position: -160% 0; }
}
</style>
