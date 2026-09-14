<script setup>
import { computed } from 'vue'
import WidgetCard from './WidgetCard.vue'
import WeatherIcon from './WeatherIcon.vue'
import { useDashboard } from '../composables/useDashboard.js'

const props = defineProps({
  payload: { type: Object, default: null }
})

const { now } = useDashboard()

const data = computed(() => props.payload?.data ?? null)

function degrees(value) {
  return (value == null || Number.isNaN(value)) ? '—' : `${Math.round(value)}°`
}

function clock(value) {
  return value ? value.slice(0, 5) : '—'
}

// "06:42:00" in Minuten seit Mitternacht. Der Tagesbogen rechnet in derselben
// Einheit wie die Ortszeit des Browsers - beides ist die Zeit vor Ort.
function minutesOfDay(value) {
  if (!value) return null
  const [hours, minutes] = value.split(':').map(Number)
  return hours * 60 + minutes
}

const daylight = computed(() => {
  if (!data.value) return null
  const sunrise = minutesOfDay(data.value.sunrise)
  const sunset = minutesOfDay(data.value.sunset)
  if (sunrise == null || sunset == null || sunset <= sunrise) return null

  const current = new Date(now.value)
  const minutes = current.getHours() * 60 + current.getMinutes()
  const raw = (minutes - sunrise) / (sunset - sunrise)
  const progress = Math.min(1, Math.max(0, raw))
  const isDay = raw > 0 && raw < 1

  // Halbe Ellipse von (10|60) nach (190|60), Scheitel bei y = 16.
  const angle = Math.PI * progress
  return {
    progress,
    isDay,
    x: 100 - 90 * Math.cos(angle),
    y: 60 - 44 * Math.sin(angle),
    remaining: isDay ? Math.round((sunset - minutes) / 60 * 10) / 10 : null
  }
})

const night = computed(() => (daylight.value ? !daylight.value.isDay : false))

const rain = computed(() => {
  const value = data.value?.precipitationProbability
  return value == null ? null : Math.min(100, Math.max(0, value))
})
</script>

<template>
  <WidgetCard title="Wetter" :payload="payload">
    <template #icon>
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M17.5 19a4.5 4.5 0 0 0 .3-9 6.5 6.5 0 0 0-12.3 1.6A3.9 3.9 0 0 0 6.5 19z" />
      </svg>
    </template>

    <div v-if="data" class="weather">
      <div class="now">
        <WeatherIcon class="now__icon" :code="data.weatherCode" :night="night" />

        <div class="now__main">
          <p class="now__temp num">{{ degrees(data.temperature) }}</p>
          <p class="now__condition">{{ data.condition }}</p>
          <p class="now__felt">gefühlt {{ degrees(data.apparentTemperature) }}</p>
        </div>

        <dl class="range">
          <div>
            <dt class="range__arrow range__arrow--up">↑</dt>
            <dd class="num">{{ degrees(data.maxTemperature) }}</dd>
          </div>
          <div>
            <dt class="range__arrow range__arrow--down">↓</dt>
            <dd class="num">{{ degrees(data.minTemperature) }}</dd>
          </div>
        </dl>
      </div>

      <div v-if="daylight" class="arc">
        <!-- Der Bogen wird auf die Kachelbreite gezogen (preserveAspectRatio
             none), die Sonne liegt als eigenes Element darueber - so bleibt sie
             bei jeder Kachelbreite rund. -->
        <div class="arc__stage">
          <svg class="arc__svg" viewBox="0 0 200 72" preserveAspectRatio="none" aria-hidden="true">
            <defs>
              <linearGradient id="arc-run" x1="0" y1="0" x2="1" y2="0">
                <stop offset="0" stop-color="#f5a623" stop-opacity="0.35" />
                <stop offset="0.55" stop-color="#ffd88a" />
                <stop offset="1" stop-color="#ff9f43" />
              </linearGradient>
            </defs>

            <path class="arc__track" d="M10 60 A 90 44 0 0 1 190 60" pathLength="1" />
            <!-- Bei Fortschritt 0 entfaellt der Pfad ganz: eine Strichlaenge
                 von 0 setzt mit runden Enden sonst einen Punkt ans Bogenende. -->
            <path
              v-if="daylight.progress > 0"
              class="arc__run"
              d="M10 60 A 90 44 0 0 1 190 60"
              pathLength="1"
              :stroke-dasharray="`${daylight.progress} 1`"
            />
            <line class="arc__ground" x1="4" y1="60" x2="196" y2="60" />
          </svg>

          <span
            v-if="daylight.isDay"
            class="arc__sun"
            :style="{ left: `${daylight.x / 2}%`, top: `${(daylight.y / 72) * 100}%` }"
            aria-hidden="true"
          ></span>
        </div>

        <div class="arc__labels">
          <span class="num">↑ {{ clock(data.sunrise) }}</span>
          <span v-if="daylight.remaining !== null" class="arc__left">noch {{ daylight.remaining }} h Tageslicht</span>
          <span v-else class="arc__left">Nacht</span>
          <span class="num">{{ clock(data.sunset) }} ↓</span>
        </div>
      </div>

      <div v-if="rain !== null" class="rain">
        <div class="rain__head">
          <span class="label">Niederschlag</span>
          <span class="num rain__value">{{ rain }} %</span>
        </div>
        <div class="rain__track">
          <div class="rain__fill" :style="{ width: `${rain}%` }"></div>
        </div>
      </div>
    </div>
  </WidgetCard>
</template>

<style scoped>
.weather {
  display: flex;
  flex-direction: column;
  gap: 1.15rem;
}

.now {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.now__icon {
  width: 58px;
  height: 58px;
  flex: none;
}

.now__main {
  min-width: 0;
}

.now__temp {
  margin: 0;
  font-size: clamp(2.6rem, 5vw, 3.3rem);
  font-weight: 400;
  line-height: 0.95;
  letter-spacing: -0.06em;
  background: linear-gradient(165deg, #ffffff 10%, #8fb4dd);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.now__condition {
  margin: 0.45rem 0 0;
  font-size: 0.95rem;
  overflow-wrap: anywhere;
}

.now__felt {
  margin: 0.1rem 0 0;
  font-size: 0.8rem;
  color: var(--text-faint);
}

.range {
  margin: 0 0 auto auto;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  text-align: right;
}

.range div {
  display: flex;
  align-items: baseline;
  justify-content: flex-end;
  gap: 0.35rem;
}

.range__arrow {
  font-size: 0.78rem;
  line-height: 1;
}

.range__arrow--up { color: var(--stale); }
.range__arrow--down { color: var(--accent); }

.range dd {
  margin: 0;
  font-size: 0.95rem;
  color: var(--text-muted);
}

/* Der Tagesbogen zeigt auf einen Blick, wo im Hellen man gerade steht -
   die reinen Uhrzeiten von Auf- und Untergang tun das nicht. */
.arc {
  padding-top: 0.2rem;
}

.arc__stage {
  position: relative;
  height: 62px;
}

.arc__svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  display: block;
}

.arc__sun {
  position: absolute;
  width: 9px;
  height: 9px;
  margin: -4.5px 0 0 -4.5px;
  border-radius: 50%;
  background: #ffd88a;
  box-shadow: 0 0 0 5px rgba(255, 181, 69, 0.18), 0 0 16px 3px rgba(255, 159, 67, 0.45);
  animation: glow 4s ease-in-out infinite;
  transition: left 0.9s var(--ease), top 0.9s var(--ease);
}

.arc__track,
.arc__run {
  fill: none;
  stroke-width: 2;
  stroke-linecap: round;
  vector-effect: non-scaling-stroke;
}

.arc__track {
  stroke: var(--line);
  stroke-dasharray: 0.008 0.018;
}

.arc__run {
  stroke: url(#arc-run);
  transition: stroke-dasharray 0.9s var(--ease);
}

.arc__ground {
  stroke: var(--line-faint);
  stroke-width: 1;
  vector-effect: non-scaling-stroke;
}

.arc__labels {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.5rem;
  margin-top: 0.35rem;
  font-size: 0.74rem;
  color: var(--text-muted);
}

.arc__left {
  color: var(--text-faint);
  font-size: 0.72rem;
  text-align: center;
  overflow-wrap: anywhere;
}

.rain__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.4rem;
}

.rain__value {
  font-size: 0.85rem;
  color: var(--text);
}

.rain__track {
  height: 5px;
  border-radius: 999px;
  background: var(--surface-sunken);
  border: 1px solid var(--line-faint);
  overflow: hidden;
}

.rain__fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, color-mix(in srgb, var(--accent) 55%, transparent), var(--accent));
  transition: width 0.8s var(--ease);
}

@keyframes glow {
  0%, 100% { box-shadow: 0 0 0 5px rgba(255, 181, 69, 0.16), 0 0 14px 2px rgba(255, 159, 67, 0.38); }
  50% { box-shadow: 0 0 0 7px rgba(255, 181, 69, 0.24), 0 0 22px 5px rgba(255, 159, 67, 0.55); }
}

@media (max-width: 380px) {
  .now__icon {
    width: 46px;
    height: 46px;
  }
}
</style>
