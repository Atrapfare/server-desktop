<script setup>
import { computed } from 'vue'
import WidgetCard from './WidgetCard.vue'
import { useDashboard } from '../composables/useDashboard.js'

const props = defineProps({
  payload: { type: Object, default: null }
})

const { now } = useDashboard()

const data = computed(() => props.payload?.data ?? null)

// Der Server siebt abgefahrene Verbindungen schon aus, aktualisiert aber nur
// alle zwei Minuten. Dazwischen sortiert die Anzeige selbst weiter, damit oben
// nie eine Bahn steht, die schon weg ist.
const connections = computed(() => {
  const list = data.value?.connections ?? []
  return list.filter((connection) => new Date(connection.departure).getTime() > now.value - 60_000)
})

const next = computed(() => connections.value[0] ?? null)
const later = computed(() => connections.value.slice(1))

function clock(iso) {
  return new Date(iso).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })
}

function countdown(connection) {
  const minutes = Math.floor((new Date(connection.departure).getTime() - now.value) / 60_000)
  if (minutes <= 0) return 'jetzt'
  if (minutes < 60) return `${minutes} Min.`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest ? `${hours} Std. ${rest} Min.` : `${hours} Std.`
}

// Faengt die Verbindung mit einem Fussweg an, ist die Abfahrtszeit der
// Aufbruch von zu Hause und nicht die Abfahrt des Fahrzeugs. Das gehoert
// dazugesagt.
function walkAhead(connection) {
  const first = connection.legs[0]
  return (first && first.walk) ? first.durationMinutes : 0
}

function rides(connection) {
  return connection.legs.filter((leg) => !leg.walk)
}

// Die Farbe folgt dem Verkehrsmittel, nicht der einzelnen Linie: Gruen fuer
// die S-Bahn, Gelb fuer die Stadtbahn, Blau fuer den Bus.
function tone(leg) {
  const product = (leg.product ?? '').toLowerCase()
  if (product.includes('s-bahn')) return 'line--rail'
  if (product.includes('stadtbahn') || product.includes('u-bahn')) return 'line--tram'
  if (product.includes('bus')) return 'line--bus'
  return 'line--other'
}

function delayClass(connection) {
  if (connection.delayMinutes >= 5) return 'delay--bad'
  if (connection.delayMinutes > 0) return 'delay--late'
  return 'delay--early'
}
</script>

<template>
  <WidgetCard title="Fahrplan" :payload="payload">
    <template #icon>
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <rect x="5" y="3" width="14" height="13" rx="3" />
        <path d="M5 9h14M8.5 20l-1.5 2M15.5 20l1.5 2M7 16.5h.01M17 16.5h.01" />
      </svg>
    </template>

    <div v-if="data" class="transit">
      <p class="route">
        <span class="route__stop">{{ data.origin }}</span>
        <span class="route__arrow" aria-hidden="true">→</span>
        <span class="route__stop">{{ data.destination }}</span>
      </p>

      <template v-if="next">
        <div class="next">
          <div class="next__head">
            <span class="next__in num">{{ countdown(next) }}</span>
            <span class="next__lines">
              <span v-for="(leg, index) in rides(next)" :key="index" class="line" :class="tone(leg)">{{ leg.line }}</span>
            </span>
          </div>

          <div class="next__facts">
            <span class="num next__time">{{ clock(next.planned) }}</span>
            <span v-if="next.delayMinutes" class="num delay" :class="delayClass(next)">
              {{ next.delayMinutes > 0 ? '+' : '' }}{{ next.delayMinutes }}
            </span>
            <span v-if="next.realtime" class="pulse" title="Echtzeitdaten" aria-hidden="true"></span>
            <span class="next__sep" aria-hidden="true">·</span>
            <span class="num">an {{ clock(next.arrival) }}</span>
            <span class="next__sep" aria-hidden="true">·</span>
            <span class="num">{{ next.durationMinutes }} Min.</span>
          </div>

          <p v-if="rides(next).length" class="next__towards">
            Richtung {{ rides(next)[0].towards }}<template v-if="rides(next)[0].platform">, Steig {{ rides(next)[0].platform }}</template>
          </p>

          <p v-if="walkAhead(next)" class="next__walk">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <circle cx="13" cy="4" r="1.6" />
              <path d="M11 21l1.5-5.5L10 13l1-5 3.5 2.5L17 11M10 8L7 10l-1 3M12.5 15.5L15 21" />
            </svg>
            Losgehen — {{ walkAhead(next) }} Min. Fußweg voraus
          </p>
        </div>

        <ul v-if="later.length" class="later">
          <li v-for="(connection, index) in later" :key="index" class="later__row">
            <span class="num later__in">{{ countdown(connection) }}</span>
            <span class="num later__time">{{ clock(connection.planned) }}</span>
            <span v-if="connection.delayMinutes" class="num delay" :class="delayClass(connection)">
              {{ connection.delayMinutes > 0 ? '+' : '' }}{{ connection.delayMinutes }}
            </span>
            <span class="later__lines">
              <span v-for="(leg, legIndex) in rides(connection)" :key="legIndex" class="line line--small" :class="tone(leg)">{{ leg.line }}</span>
            </span>
            <span class="num later__duration">{{ connection.durationMinutes }} Min.</span>
          </li>
        </ul>
      </template>

      <p v-else class="empty">Heute fährt nichts mehr.</p>
    </div>
  </WidgetCard>
</template>

<style scoped>
.transit {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
}

.route {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin: 0;
  font-size: 0.82rem;
  color: var(--text-muted);
}

.route__arrow {
  color: var(--text-faint);
}

/* Die naechste Verbindung ist der eigentliche Grund fuer die Kachel und
   bekommt deshalb eigene Flaeche statt nur die erste Zeile zu sein. */
.next {
  border: 1px solid var(--line-faint);
  border-radius: var(--radius-sm);
  background: linear-gradient(180deg, color-mix(in srgb, var(--accent) 9%, transparent), transparent);
  padding: 0.75rem 0.85rem 0.8rem;
}

.next__head {
  display: flex;
  align-items: baseline;
  gap: 0.7rem;
}

.next__in {
  font-size: clamp(1.7rem, 3.4vw, 2.1rem);
  line-height: 1;
  letter-spacing: -0.04em;
  background: linear-gradient(165deg, #ffffff 10%, #8fb4dd);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.next__lines {
  margin-left: auto;
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem;
}

.line {
  display: inline-grid;
  place-items: center;
  min-width: 2.1rem;
  padding: 0.1rem 0.4rem;
  border-radius: 5px;
  font-family: var(--font-mono);
  font-size: 0.76rem;
  font-weight: 600;
  letter-spacing: 0.01em;
  color: var(--bg-deep);
  background: var(--text-muted);
}

.line--small {
  min-width: 1.8rem;
  font-size: 0.68rem;
  padding: 0.05rem 0.3rem;
}

.line--bus { background: var(--accent); }
.line--rail { background: var(--ok); }
.line--tram { background: var(--stale); }
.line--other { background: var(--text-muted); }

.next__facts {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.35rem;
  margin-top: 0.5rem;
  font-size: 0.78rem;
  color: var(--text-muted);
}

.next__time {
  color: var(--text);
  font-size: 0.85rem;
}

.next__sep {
  color: var(--text-faint);
  opacity: 0.6;
}

.next__towards,
.next__walk {
  margin: 0.35rem 0 0;
  font-size: 0.76rem;
  color: var(--text-faint);
  overflow-wrap: anywhere;
}

.next__walk {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  color: var(--accent-soft);
}

.next__walk svg {
  width: 13px;
  height: 13px;
  flex: none;
}

.delay {
  font-size: 0.76rem;
  font-weight: 600;
}

.delay--late { color: var(--stale); }
.delay--bad { color: var(--error); }
.delay--early { color: var(--ok); }

/* Echtzeit statt Fahrplan: der Punkt schlaegt ruhig mit, damit erkennbar ist,
   welche Zeit tatsaechlich gemeldet wurde. */
.pulse {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--ok);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--ok) 18%, transparent);
  animation: beat 2.4s var(--ease) infinite;
}

.later {
  list-style: none;
  margin: 0;
  padding: 0;
}

.later__row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.4rem 0;
  font-size: 0.8rem;
  color: var(--text-muted);
}

.later__row + .later__row {
  border-top: 1px solid var(--line-faint);
}

.later__in {
  width: 4.6rem;
  flex: none;
  color: var(--text);
}

.later__time {
  font-size: 0.76rem;
  color: var(--text-faint);
}

.later__lines {
  margin-left: auto;
  display: flex;
  gap: 0.25rem;
}

.later__duration {
  width: 3.7rem;
  flex: none;
  text-align: right;
  font-size: 0.74rem;
  color: var(--text-faint);
}

.empty {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.92rem;
}

@keyframes beat {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}
</style>
