<script setup>
import { computed } from 'vue'
import WidgetCard from './WidgetCard.vue'
import { useDashboard } from '../composables/useDashboard.js'

const props = defineProps({
  payload: { type: Object, default: null }
})

const { now } = useDashboard()

const events = computed(() => props.payload?.data?.events ?? [])
const failed = computed(() => props.payload?.data?.failedSources ?? [])

// Die Herkunft steht nur an den Terminen, wenn mehrere Kalender eingetragen
// sind - bei einem einzigen waere sie an jeder Zeile dieselbe Auskunft.
const showSource = computed(() => (props.payload?.data?.sourceCount ?? 1) > 1)

const GROUP_LABEL = {
  HEUTE: 'Heute',
  MORGEN: 'Morgen',
  SPAETER: 'Später'
}

function clock(iso) {
  return new Date(iso).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })
}

function day(iso) {
  return new Date(iso).toLocaleDateString('de-DE', { weekday: 'short', day: 'numeric', month: 'short' })
}

// Der laufende Termin gehoert unter "Heute" - sonst bekaeme er eine eigene
// Zwischenueberschrift, obwohl er am selben Tag stattfindet.
const groups = computed(() => {
  const result = []
  for (const event of events.value) {
    const key = event.state === 'LAUFEND' ? 'HEUTE' : event.state
    const last = result[result.length - 1]
    if (!last || last.key !== key) {
      result.push({ key, label: GROUP_LABEL[key] ?? '', items: [event] })
    }
    else {
      last.items.push(event)
    }
  }
  return result
})

// Nur der naechste anstehende Termin bekommt den Countdown. Auf jeder Zeile
// waere es Rauschen, an einer Zeile ist es die Antwort auf "was kommt jetzt".
const nextUp = computed(() => events.value.find(
  (event) => event.state !== 'LAUFEND' && !event.allDay && new Date(event.start).getTime() > now.value
))

function startsIn(event) {
  const minutes = Math.round((new Date(event.start).getTime() - now.value) / 60_000)
  if (minutes < 1) return 'gleich'
  if (minutes < 60) return `in ${minutes} Min.`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `in ${hours} Std.`
  return `in ${Math.round(hours / 24)} Tagen`
}

function progress(event) {
  const start = new Date(event.start).getTime()
  const end = new Date(event.end).getTime()
  if (!(end > start)) return 0
  return Math.min(100, Math.max(0, ((now.value - start) / (end - start)) * 100))
}
</script>

<template>
  <WidgetCard title="Termine" :payload="payload">
    <template #icon>
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <rect x="3" y="5" width="18" height="16" rx="3" />
        <path d="M3 10h18M8 3v4M16 3v4" />
      </svg>
    </template>

    <p v-if="failed.length" class="failed">
      <span class="failed__mark" aria-hidden="true">!</span>
      Ohne {{ failed.map((source) => source.name).join(', ') }} — Kalender nicht erreichbar
    </p>

    <div v-if="events.length" class="agenda">
      <section v-for="group in groups" :key="group.key" class="group">
        <p class="group__label label">{{ group.label }}</p>

        <ul class="events">
          <li
            v-for="(event, index) in group.items"
            :key="`${event.title}-${event.start}-${index}`"
            class="event"
            :class="{
              'event--running': event.state === 'LAUFEND',
              'event--next': event === nextUp,
              'event--allday': event.allDay
            }"
          >
            <span class="event__time">
              <template v-if="event.allDay">
                <span class="event__dash" aria-hidden="true">—</span>
              </template>
              <template v-else>
                <span class="num event__start">{{ clock(event.start) }}</span>
                <span class="num event__end">{{ clock(event.end) }}</span>
              </template>
            </span>

            <span class="event__rail" aria-hidden="true">
              <span class="event__node"></span>
            </span>

            <span class="event__body">
              <span class="event__title">
                {{ event.title }}
                <span v-if="event.allDay" class="event__tag">ganztägig</span>
              </span>

              <span class="event__sub">
                <span v-if="showSource && event.source" class="event__source">{{ event.source }}</span>
                <span v-if="group.key === 'SPAETER'" class="event__date num">{{ day(event.start) }}</span>
                <span v-if="event.location" class="event__location">{{ event.location }}</span>
              </span>

              <span v-if="event === nextUp" class="event__countdown num">{{ startsIn(event) }}</span>

              <span v-if="event.state === 'LAUFEND'" class="event__progress">
                <span class="event__progress-fill" :style="{ width: `${progress(event)}%` }"></span>
              </span>
            </span>
          </li>
        </ul>
      </section>
    </div>

    <p v-else class="empty">Nichts weiter anstehen — freier Tag.</p>
  </WidgetCard>
</template>

<style scoped>
.agenda {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.group__label {
  margin: 0 0 0.4rem;
  font-size: 0.63rem;
}

.events {
  list-style: none;
  margin: 0;
  padding: 0;
}

.event {
  position: relative;
  display: grid;
  grid-template-columns: 3.3rem 14px 1fr;
  gap: 0 0.6rem;
  padding: 0.42rem 0.55rem 0.42rem 0;
  border-radius: var(--radius-sm);
  min-width: 0;
}

.event__time {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  line-height: 1.25;
  padding-top: 0.1rem;
}

.event__start {
  font-size: 0.84rem;
  color: var(--text);
}

.event__end {
  font-size: 0.7rem;
  color: var(--text-faint);
}

/* Ganztaegige Termine haben keine Uhrzeit. Statt das Wort "ganztaegig" in die
   schmale Zeitspalte zu quetschen, steht dort ein Strich und die Kennzeichnung
   wandert als Marke hinter den Titel. */
.event__dash {
  font-size: 0.8rem;
  color: var(--text-faint);
}

.event__tag {
  display: inline-block;
  margin-left: 0.35rem;
  transform: translateY(-1px);
  font-family: var(--font-mono);
  font-size: 0.6rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-faint);
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 0.05rem 0.4rem;
  white-space: nowrap;
}

/* Die Schiene zieht sich durch alle Zeilen einer Gruppe und macht aus der
   Liste eine Zeitachse. */
.event__rail {
  position: relative;
  display: flex;
  justify-content: center;
  padding-top: 0.32rem;
}

.event__rail::before {
  content: '';
  position: absolute;
  top: 0;
  bottom: -0.85rem;
  width: 1px;
  background: var(--line-strong);
}

.event:last-child .event__rail::before {
  bottom: auto;
  height: 0.4rem;
}

.event__node {
  position: relative;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--surface-raised);
  border: 1.5px solid var(--line-strong);
  z-index: 1;
}

.event__body {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  min-width: 0;
  padding-bottom: 0.1rem;
}

.event__title {
  font-size: 0.95rem;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.event__sub {
  display: flex;
  flex-wrap: wrap;
  gap: 0.2rem 0.5rem;
  font-size: 0.75rem;
  color: var(--text-faint);
}

.event__date {
  color: var(--text-muted);
}

/* Herkunft als ruhige Marke: sie soll zuzuordnen sein, ohne mit dem Titel um
   Aufmerksamkeit zu ringen. */
.event__source {
  font-family: var(--font-mono);
  font-size: 0.64rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--accent-soft);
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  border-radius: 4px;
  padding: 0.02rem 0.3rem;
  white-space: nowrap;
}

.failed {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  margin: 0 0 0.7rem;
  font-size: 0.75rem;
  color: var(--stale);
}

.failed__mark {
  display: grid;
  place-items: center;
  width: 15px;
  height: 15px;
  flex: none;
  border-radius: 50%;
  font-size: 0.6rem;
  font-weight: 700;
  color: var(--bg);
  background: var(--stale);
}

.event__location {
  overflow-wrap: anywhere;
}

.event__countdown {
  align-self: flex-start;
  margin-top: 0.2rem;
  font-size: 0.66rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--accent);
  border: 1px solid color-mix(in srgb, var(--accent) 30%, transparent);
  background: color-mix(in srgb, var(--accent) 10%, transparent);
  border-radius: 999px;
  padding: 0.1rem 0.45rem;
}

/* Der laufende Termin bekommt Flaeche statt nur Farbe - er ist die einzige
   Zeile, die gerade tatsaechlich passiert. */
.event--running {
  background: linear-gradient(90deg, color-mix(in srgb, var(--accent) 13%, transparent), transparent 85%);
  padding-left: 0.55rem;
  margin-left: -0.55rem;
  grid-template-columns: 3.3rem 14px 1fr;
}

.event--running .event__node {
  background: var(--accent);
  border-color: var(--accent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 20%, transparent);
}

.event--running .event__start {
  color: var(--accent-soft);
}

.event--next .event__node {
  border-color: var(--accent);
}

.event__progress {
  display: block;
  height: 3px;
  margin-top: 0.45rem;
  border-radius: 999px;
  background: color-mix(in srgb, var(--accent) 16%, transparent);
  overflow: hidden;
}

.event__progress-fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--accent), var(--cyan));
  transition: width 1s linear;
}

.empty {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.92rem;
}
</style>
