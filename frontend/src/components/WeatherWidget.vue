<script setup>
import { computed } from 'vue'
import WidgetCard from './WidgetCard.vue'

const props = defineProps({
  payload: { type: Object, default: null }
})

const data = computed(() => props.payload?.data ?? null)

function degrees(value) {
  return (value == null || Number.isNaN(value)) ? '—' : `${Math.round(value)}°`
}

function clock(value) {
  return value ? value.slice(0, 5) : '—'
}
</script>

<template>
  <WidgetCard title="Wetter" :payload="payload">
    <div v-if="data" class="weather">
      <div class="weather__now">
        <span class="weather__temp">{{ degrees(data.temperature) }}</span>
        <div class="weather__desc">
          <span class="weather__condition">{{ data.condition }}</span>
          <span class="weather__felt">gefühlt {{ degrees(data.apparentTemperature) }}</span>
        </div>
      </div>

      <dl class="weather__facts">
        <div>
          <dt>Heute</dt>
          <dd>{{ degrees(data.maxTemperature) }} / {{ degrees(data.minTemperature) }}</dd>
        </div>
        <div>
          <dt>Niederschlag</dt>
          <dd>{{ data.precipitationProbability }} %</dd>
        </div>
        <div>
          <dt>Aufgang</dt>
          <dd>{{ clock(data.sunrise) }}</dd>
        </div>
        <div>
          <dt>Untergang</dt>
          <dd>{{ clock(data.sunset) }}</dd>
        </div>
      </dl>
    </div>
  </WidgetCard>
</template>

<style scoped>
.weather {
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
}

.weather__now {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.weather__temp {
  font-size: 3.1rem;
  font-weight: 300;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.weather__desc {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.weather__condition {
  font-size: 1rem;
}

.weather__felt {
  font-size: 0.85rem;
  color: var(--text-muted);
}

.weather__facts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(80px, 1fr));
  gap: 0.75rem 1rem;
  margin: 0;
  padding-top: 0.9rem;
  border-top: 1px solid var(--border);
}

.weather__facts div {
  min-width: 0;
}

.weather__facts dt {
  font-size: 0.72rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--text-faint);
}

.weather__facts dd {
  margin: 0.15rem 0 0;
  font-size: 1rem;
  font-variant-numeric: tabular-nums;
}
</style>
