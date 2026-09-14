<script setup>
import { computed } from 'vue'

const props = defineProps({
  code: { type: Number, default: null },
  night: { type: Boolean, default: false }
})

// Die WMO-Codes des Backends auf die wenigen Bilder abbilden, die sich auf
// Icon-Groesse noch unterscheiden lassen. Feinere Abstufungen ("leichter" vs.
// "starker" Regen) traegt der Text daneben, nicht das Symbol.
const kind = computed(() => {
  const code = props.code
  if (code == null) return 'cloudy'
  if (code === 0) return 'clear'
  if (code === 1 || code === 2) return 'partly'
  if (code === 3) return 'cloudy'
  if (code === 45 || code === 48) return 'fog'
  if (code >= 51 && code <= 57) return 'drizzle'
  if (code >= 71 && code <= 77) return 'snow'
  if (code === 85 || code === 86) return 'snow'
  if (code >= 95) return 'thunder'
  return 'rain'
})

const hasCloud = computed(() => kind.value !== 'clear' && kind.value !== 'fog')
const hasSun = computed(() => kind.value === 'clear' || kind.value === 'partly')
</script>

<template>
  <svg class="icon" :class="`icon--${kind}`" viewBox="0 0 48 48" role="img" :aria-label="kind">
    <defs>
      <linearGradient id="wi-sun" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0" stop-color="#ffd88a" />
        <stop offset="1" stop-color="#f5a623" />
      </linearGradient>
      <linearGradient id="wi-moon" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0" stop-color="#e8f0ff" />
        <stop offset="1" stop-color="#9db4d6" />
      </linearGradient>
      <linearGradient id="wi-cloud" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stop-color="#c3d2e6" />
        <stop offset="1" stop-color="#7d90aa" />
      </linearGradient>
    </defs>

    <g v-if="hasSun" class="sun" :class="{ 'sun--moon': night }">
      <template v-if="night">
        <path
          class="sun__body"
          d="M31.5 8.6a9.4 9.4 0 1 0 8.2 12.6 7.6 7.6 0 0 1-8.2-12.6z"
          fill="url(#wi-moon)"
        />
      </template>
      <template v-else>
        <g class="sun__rays" stroke="url(#wi-sun)" stroke-width="2.4" stroke-linecap="round">
          <path d="M28 2v5M28 33v5M13.5 17.5h5M42.5 17.5h-5M17.7 7.2l3.5 3.5M41.3 27.8l-3.5-3.5M17.7 27.8l3.5-3.5M41.3 7.2l-3.5 3.5" />
        </g>
        <circle class="sun__body" cx="28" cy="17.5" r="8" fill="url(#wi-sun)" />
      </template>
    </g>

    <g v-if="hasCloud" class="cloud" fill="url(#wi-cloud)">
      <circle cx="18" cy="27" r="7" />
      <circle cx="28.5" cy="24.5" r="9" />
      <rect x="12" y="26.5" width="24" height="9.5" rx="4.75" />
    </g>

    <g v-if="kind === 'fog'" class="fog" stroke="url(#wi-cloud)" stroke-width="3" stroke-linecap="round">
      <path class="fog__line" d="M9 18h30" />
      <path class="fog__line" d="M13 26h26" />
      <path class="fog__line" d="M9 34h30" />
    </g>

    <g v-if="kind === 'rain' || kind === 'drizzle'" class="rain" stroke="#5db2ff" stroke-width="2.6" stroke-linecap="round">
      <path class="drop" d="M16 38.5v4" />
      <path class="drop" d="M24 39v5" />
      <path class="drop" d="M32 38.5v4" />
    </g>

    <g v-if="kind === 'snow'" class="snow" fill="#d9e8ff">
      <circle class="flake" cx="16" cy="40" r="2.1" />
      <circle class="flake" cx="24" cy="41" r="2.1" />
      <circle class="flake" cx="32" cy="40" r="2.1" />
    </g>

    <path
      v-if="kind === 'thunder'"
      class="bolt"
      d="M25.5 36.5h6l-9 10 2-6.5h-5.5l8-9.5-1.5 6z"
      fill="url(#wi-sun)"
    />
  </svg>
</template>

<style scoped>
.icon {
  width: 100%;
  height: 100%;
  display: block;
  overflow: visible;
}

.sun__rays {
  transform-origin: 28px 17.5px;
  animation: turn 32s linear infinite;
}

.sun__body {
  filter: drop-shadow(0 0 10px rgba(245, 166, 35, 0.35));
}

.sun--moon .sun__body {
  filter: drop-shadow(0 0 10px rgba(157, 180, 214, 0.3));
}

/* Bei "teils bewoelkt" schiebt sich die Wolke vor die Sonne. Sie muss weit
   genug nach oben rechts ruecken, sonst verschluckt die Wolke sie ganz -
   besonders die Mondsichel, die schmaler ist als die Sonnenscheibe. */
.icon--partly .sun {
  transform: translate(7px, -8px) scale(0.92);
  transform-origin: 28px 17.5px;
}

.icon--partly .cloud,
.icon--cloudy .cloud {
  animation: drift 9s ease-in-out infinite;
}

.drop,
.flake {
  animation: fall 1.6s linear infinite;
}

.drop:nth-child(2),
.flake:nth-child(2) { animation-delay: 0.45s; }
.drop:nth-child(3),
.flake:nth-child(3) { animation-delay: 0.9s; }

.icon--drizzle .drop {
  animation-duration: 2.4s;
}

.fog__line {
  animation: haze 6s ease-in-out infinite;
}

.fog__line:nth-child(2) { animation-delay: 0.8s; }
.fog__line:nth-child(3) { animation-delay: 1.6s; }

.bolt {
  filter: drop-shadow(0 0 8px rgba(245, 166, 35, 0.45));
  animation: flash 3.4s ease-in-out infinite;
}

@keyframes turn {
  to { transform: rotate(360deg); }
}

@keyframes drift {
  0%, 100% { transform: translateX(0); }
  50% { transform: translateX(2.5px); }
}

@keyframes fall {
  0% { opacity: 0; transform: translateY(-5px); }
  25% { opacity: 1; }
  100% { opacity: 0; transform: translateY(6px); }
}

@keyframes haze {
  0%, 100% { transform: translateX(-2px); opacity: 0.65; }
  50% { transform: translateX(2px); opacity: 1; }
}

@keyframes flash {
  0%, 62%, 70%, 100% { opacity: 1; }
  66% { opacity: 0.25; }
}
</style>
