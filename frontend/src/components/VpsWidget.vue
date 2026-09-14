<script setup>
import { computed } from 'vue'
import WidgetCard from './WidgetCard.vue'

const props = defineProps({
  payload: { type: Object, default: null }
})

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

const loadRatio = computed(() => {
  if (!data.value) return 0
  return data.value.load[0] / Math.max(1, data.value.cpuCount)
})

const memory = computed(() => {
  if (!data.value) return null
  const { usedMb, totalMb } = data.value.memory
  return { used: usedMb, total: totalMb, ratio: totalMb ? usedMb / totalMb : 0, unit: 'MB' }
})

const disk = computed(() => {
  if (!data.value) return null
  const { usedGb, totalGb } = data.value.disk
  return { used: usedGb, total: totalGb, ratio: totalGb ? usedGb / totalGb : 0, unit: 'GB' }
})

function level(ratio) {
  if (ratio >= 0.9) return 'bar__fill--critical'
  if (ratio >= 0.75) return 'bar__fill--warn'
  return ''
}

function percent(ratio) {
  return `${Math.round(ratio * 100)} %`
}
</script>

<template>
  <WidgetCard title="VPS" :payload="payload">
    <p v-if="unreachable" class="down">Nicht erreichbar</p>

    <div v-if="data" class="vps">
      <div class="vps__head">
        <span class="vps__host">{{ data.hostname }}</span>
        <span class="vps__uptime">seit {{ uptime }}</span>
      </div>

      <div class="metric">
        <div class="metric__label">
          <span>Last</span>
          <span class="metric__value">
            {{ data.load.map((value) => value.toFixed(2)).join('  ') }}
            <span class="metric__hint">auf {{ data.cpuCount }} Kernen</span>
          </span>
        </div>
        <div class="bar">
          <div class="bar__fill" :class="level(loadRatio)" :style="{ width: `${Math.min(100, loadRatio * 100)}%` }"></div>
        </div>
      </div>

      <div class="metric">
        <div class="metric__label">
          <span>RAM</span>
          <span class="metric__value">
            {{ memory.used }} / {{ memory.total }} {{ memory.unit }}
            <span class="metric__hint">{{ percent(memory.ratio) }}</span>
          </span>
        </div>
        <div class="bar">
          <div class="bar__fill" :class="level(memory.ratio)" :style="{ width: `${memory.ratio * 100}%` }"></div>
        </div>
      </div>

      <div class="metric">
        <div class="metric__label">
          <span>Platte</span>
          <span class="metric__value">
            {{ disk.used }} / {{ disk.total }} {{ disk.unit }}
            <span class="metric__hint">{{ percent(disk.ratio) }}</span>
          </span>
        </div>
        <div class="bar">
          <div class="bar__fill" :class="level(disk.ratio)" :style="{ width: `${disk.ratio * 100}%` }"></div>
        </div>
      </div>
    </div>
  </WidgetCard>
</template>

<style scoped>
.down {
  margin: 0 0 0.3rem;
  font-size: 1.05rem;
  font-weight: 600;
  color: var(--error);
}

.vps {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.vps__head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.vps__host {
  font-size: 1.05rem;
  overflow-wrap: anywhere;
}

.vps__uptime {
  font-size: 0.82rem;
  color: var(--text-muted);
  white-space: nowrap;
}

.metric__label {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 0.75rem;
  font-size: 0.8rem;
  color: var(--text-muted);
  margin-bottom: 0.3rem;
}

.metric__value {
  font-variant-numeric: tabular-nums;
  color: var(--text);
}

.metric__hint {
  color: var(--text-faint);
  margin-left: 0.35rem;
}

.bar {
  height: 6px;
  border-radius: 999px;
  background: var(--surface-raised);
  overflow: hidden;
}

.bar__fill {
  height: 100%;
  border-radius: 999px;
  background: var(--accent);
  transition: width 0.4s ease;
}

.bar__fill--warn { background: var(--stale); }
.bar__fill--critical { background: var(--error); }

@media (prefers-reduced-motion: reduce) {
  .bar__fill { transition: none; }
}
</style>
