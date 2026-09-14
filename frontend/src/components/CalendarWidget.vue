<script setup>
import { computed } from 'vue'
import WidgetCard from './WidgetCard.vue'

const props = defineProps({
  payload: { type: Object, default: null }
})

const events = computed(() => props.payload?.data ?? [])

function clock(iso) {
  return new Date(iso).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })
}

function timeLabel(event) {
  if (event.allDay) {
    return 'ganztägig'
  }
  return `${clock(event.start)}–${clock(event.end)}`
}

const dayLabel = {
  MORGEN: 'morgen',
  SPAETER: 'später'
}
</script>

<template>
  <WidgetCard title="Termine" :payload="payload">
    <ul v-if="events.length" class="events">
      <li
        v-for="(event, index) in events"
        :key="`${event.title}-${event.start}-${index}`"
        class="event"
        :class="{ 'event--running': event.state === 'LAUFEND' }"
      >
        <span class="event__time" :class="{ 'event__time--allday': event.allDay }">
          {{ timeLabel(event) }}
        </span>
        <span class="event__body">
          <span class="event__title">
            {{ event.title }}
            <span v-if="dayLabel[event.state]" class="event__day">{{ dayLabel[event.state] }}</span>
          </span>
          <span v-if="event.location" class="event__location">{{ event.location }}</span>
        </span>
      </li>
    </ul>

    <p v-else class="empty">Nichts weiter anstehen — freier Tag.</p>
  </WidgetCard>
</template>

<style scoped>
.events {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.event {
  display: flex;
  gap: 0.85rem;
  padding: 0.55rem 0.6rem 0.55rem 0.5rem;
  border-radius: 7px;
  border-left: 3px solid transparent;
  min-width: 0;
}

.event + .event {
  margin-top: 0.15rem;
}

.event--running {
  background: color-mix(in srgb, var(--accent) 14%, transparent);
  border-left-color: var(--accent);
}

.event__time {
  flex: none;
  width: 6.6rem;
  font-variant-numeric: tabular-nums;
  font-size: 0.88rem;
  color: var(--text-muted);
  padding-top: 0.1rem;
}

.event__time--allday {
  font-variant-numeric: normal;
  font-style: italic;
  color: var(--text-faint);
}

.event__body {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.event__title {
  overflow-wrap: anywhere;
}

.event__day {
  font-size: 0.7rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--text-faint);
  border: 1px solid var(--border);
  border-radius: 999px;
  padding: 0.05rem 0.4rem;
  margin-left: 0.4rem;
  white-space: nowrap;
}

.event__location {
  font-size: 0.8rem;
  color: var(--text-faint);
  overflow-wrap: anywhere;
}

.empty {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.92rem;
}
</style>
