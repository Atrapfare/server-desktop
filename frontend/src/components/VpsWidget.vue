<script setup>
import { computed } from 'vue'
import WidgetCard from './WidgetCard.vue'

const props = defineProps({
  payload: { type: Object, default: null }
})

const SEGMENTS = 28

const data = computed(() => props.payload?.data ?? null)

// Beim VPS ist der Ausfall selbst die Information - anders als bei den uebrigen
// Kacheln reicht ein dezenter Veraltet-Hinweis hier nicht.
const unreachable = computed(() => ['STALE', 'ERROR'].includes(props.payload?.status))

const uptime = computed(() => {
  if (!data.value) return '—'
  const total = data.value.uptimeSeconds
  const days = Math.floor(total / 86400)
  const hours = Math.floor((total % 86400) / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  if (days > 0) return `${days} ${days === 1 ? 'Tag' : 'Tage'}, ${hours} Std.`
  if (hours > 0) return `${hours} Std., ${minutes} Min.`
  return `${minutes} Min.`
})

function number(value, digits = 1) {
  return value.toLocaleString('de-DE', { minimumFractionDigits: digits, maximumFractionDigits: digits })
}

// Unter einem Gigabyte bleibt MB die ehrlichere Einheit, darueber liest sich
// "3,4 / 7,8 GB" deutlich schneller als fuenfstellige Megabyte.
function size(usedMb, totalMb) {
  if (totalMb >= 1024) {
    return `${number(usedMb / 1024)} / ${number(totalMb / 1024)} GB`
  }
  return `${Math.round(usedMb)} / ${Math.round(totalMb)} MB`
}

const metrics = computed(() => {
  if (!data.value) return []
  const { load, cpuCount, memory, disk } = data.value
  return [
    {
      key: 'load',
      label: 'Last',
      ratio: load[0] / Math.max(1, cpuCount),
      value: number(load[0], 2),
      foot: `${load.map((entry) => number(entry, 2)).join('  ·  ')}   auf ${cpuCount} ${cpuCount === 1 ? 'Kern' : 'Kernen'}`
    },
    {
      key: 'ram',
      label: 'Arbeitsspeicher',
      ratio: memory.totalMb ? memory.usedMb / memory.totalMb : 0,
      value: size(memory.usedMb, memory.totalMb),
      foot: `${number(Math.max(0, memory.totalMb - memory.usedMb) / (memory.totalMb >= 1024 ? 1024 : 1))} ${memory.totalMb >= 1024 ? 'GB' : 'MB'} frei`
    },
    {
      key: 'disk',
      label: 'Platte',
      ratio: disk.totalGb ? disk.usedGb / disk.totalGb : 0,
      value: `${number(disk.usedGb, 0)} / ${number(disk.totalGb, 0)} GB`,
      foot: `${number(Math.max(0, disk.totalGb - disk.usedGb), 0)} GB frei`
    }
  ]
})

// Die Segmente faerben sich nach ihrer eigenen Position, nicht nach dem
// Gesamtwert: der Ausschlag laeuft wie bei einer Aussteuerungsanzeige von
// Gruen ueber Gelb nach Rot, statt auf einen Schlag umzuspringen.
function segmentClass(index, ratio) {
  const filled = Math.round(Math.min(1, Math.max(0, ratio)) * SEGMENTS)
  if (index >= filled) return 'seg'
  const position = (index + 1) / SEGMENTS
  if (position > 0.9) return 'seg seg--on seg--critical'
  if (position > 0.75) return 'seg seg--on seg--warn'
  return 'seg seg--on'
}

function percent(ratio) {
  return `${Math.round(Math.min(1, Math.max(0, ratio)) * 100)} %`
}
</script>

<template>
  <WidgetCard title="VPS" :payload="payload">
    <template #icon>
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
        <rect x="3" y="4" width="18" height="7" rx="2" />
        <rect x="3" y="13" width="18" height="7" rx="2" />
        <path d="M7 7.5h.01M7 16.5h.01" />
      </svg>
    </template>

    <div class="vps">
      <div class="host">
        <div class="host__id">
          <span class="host__name">{{ data?.hostname ?? 'Unbekannt' }}</span>
          <span v-if="data" class="host__uptime num">seit {{ uptime }}</span>
        </div>
        <span v-if="unreachable" class="host__down">offline</span>
      </div>

      <div v-if="data" class="metrics">
        <div v-for="metric in metrics" :key="metric.key" class="metric">
          <div class="metric__top">
            <span class="label">{{ metric.label }}</span>
            <span class="metric__value num">{{ metric.value }}</span>
          </div>

          <div class="meter" role="meter" :aria-valuenow="Math.round(metric.ratio * 100)" :aria-label="metric.label">
            <span
              v-for="index in SEGMENTS"
              :key="index"
              :class="segmentClass(index - 1, metric.ratio)"
              :style="{ '--step': index }"
            ></span>
          </div>

          <div class="metric__foot">
            <span class="num metric__hint">{{ metric.foot }}</span>
            <span class="num metric__pct">{{ percent(metric.ratio) }}</span>
          </div>
        </div>
      </div>
    </div>
  </WidgetCard>
</template>

<style scoped>
.vps {
  display: flex;
  flex-direction: column;
  gap: 1.05rem;
}

.host {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.host__id {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 0.2rem 0.6rem;
  min-width: 0;
}

.host__name {
  font-family: var(--font-mono);
  font-size: 1.05rem;
  letter-spacing: -0.02em;
  overflow-wrap: anywhere;
}

.host__uptime {
  font-size: 0.74rem;
  color: var(--text-faint);
}

.host__down {
  flex: none;
  font-family: var(--font-mono);
  font-size: 0.66rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--error);
  border: 1px solid color-mix(in srgb, var(--error) 35%, transparent);
  background: color-mix(in srgb, var(--error) 12%, transparent);
  border-radius: 999px;
  padding: 0.12rem 0.5rem;
}

.metrics {
  display: flex;
  flex-direction: column;
  gap: 0.95rem;
}

.metric__top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.42rem;
}

.metric__value {
  font-size: 0.85rem;
  color: var(--text);
}

.meter {
  display: flex;
  gap: 2px;
  height: 14px;
}

.seg {
  flex: 1;
  border-radius: 1.5px;
  background: var(--surface-raised);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.015);
  transition: background 0.5s var(--ease), box-shadow 0.5s var(--ease);
  animation: light 0.5s var(--ease) both;
  animation-delay: calc(var(--step) * 14ms);
}

.seg--on {
  background: var(--accent);
  box-shadow: 0 0 7px color-mix(in srgb, var(--accent) 45%, transparent);
}

.seg--warn {
  background: var(--stale);
  box-shadow: 0 0 7px color-mix(in srgb, var(--stale) 45%, transparent);
}

.seg--critical {
  background: var(--error);
  box-shadow: 0 0 8px color-mix(in srgb, var(--error) 55%, transparent);
}

.metric__foot {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.75rem;
  margin-top: 0.4rem;
  font-size: 0.7rem;
  color: var(--text-faint);
}

.metric__hint {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.metric__pct {
  flex: none;
  color: var(--text-muted);
}

@keyframes light {
  from { transform: scaleY(0.35); opacity: 0; }
  to { transform: none; opacity: 1; }
}
</style>
