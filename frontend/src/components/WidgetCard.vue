<script setup>
import { computed } from 'vue'
import { formatRelative, useDashboard } from '../composables/useDashboard.js'

const props = defineProps({
  title: { type: String, required: true },
  payload: { type: Object, default: null }
})

const { now } = useDashboard()

const status = computed(() => props.payload?.status ?? 'LOADING')
const isStale = computed(() => status.value === 'STALE')
const isError = computed(() => status.value === 'ERROR')
const hasData = computed(() => props.payload?.data != null)
const age = computed(() => formatRelative(props.payload?.lastUpdated, now.value))
</script>

<template>
  <section class="card" :class="`card--${status.toLowerCase()}`">
    <header class="card__head">
      <h2 class="card__title">{{ title }}</h2>
      <span class="card__meta">
        <span class="dot" :class="`dot--${status.toLowerCase()}`" aria-hidden="true"></span>
        <span class="card__age">{{ age }}</span>
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
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 1.15rem 1.25rem 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  min-width: 0;
}

.card__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
}

.card__title {
  margin: 0;
  font-size: 0.9rem;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.card__meta {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  white-space: nowrap;
}

.card__age {
  font-size: 0.78rem;
  color: var(--text-faint);
  font-variant-numeric: tabular-nums;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--text-faint);
  flex: none;
}

.dot--ok { background: var(--ok); }
.dot--stale { background: var(--stale); }
.dot--error { background: var(--error); }

.card__body {
  min-width: 0;
}

/* Veraltete Werte bleiben lesbar, treten aber sichtbar zurueck. */
.card__body--stale {
  opacity: 0.55;
}

.notice {
  margin: 0;
  font-size: 0.82rem;
  border-radius: 6px;
  padding: 0.5rem 0.65rem;
  overflow-wrap: anywhere;
}

.notice--stale {
  color: var(--stale);
  background: color-mix(in srgb, var(--stale) 12%, transparent);
}

.notice--error {
  color: var(--error);
  background: color-mix(in srgb, var(--error) 12%, transparent);
}

.skeleton {
  height: 1.6rem;
  border-radius: 6px;
  background: linear-gradient(90deg, var(--surface-raised) 25%, var(--border) 50%, var(--surface-raised) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s ease-in-out infinite;
  margin-bottom: 0.5rem;
}

.skeleton--short {
  width: 55%;
  margin-bottom: 0;
}

@keyframes shimmer {
  from { background-position: 200% 0; }
  to { background-position: -200% 0; }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton { animation: none; }
}
</style>
