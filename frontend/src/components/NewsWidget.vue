<script setup>
import { computed } from 'vue'
import WidgetCard from './WidgetCard.vue'
import { formatRelative, useDashboard } from '../composables/useDashboard.js'

const props = defineProps({
  payload: { type: Object, default: null }
})

const { now } = useDashboard()

const items = computed(() => props.payload?.data?.items ?? [])
const failed = computed(() => props.payload?.data?.failedFeeds ?? [])

// Alles aus der letzten halben Stunde gilt als frisch und bekommt einen Punkt.
const FRESH_MS = 30 * 60_000

function isFresh(item) {
  return item.publishedAt != null && now.value - new Date(item.publishedAt).getTime() < FRESH_MS
}
</script>

<template>
  <WidgetCard title="Nachrichten" :payload="payload">
    <template #icon>
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M4 5h13v14H6a2 2 0 0 1-2-2z" />
        <path d="M17 9h3v8a2 2 0 0 1-3 1.7" />
        <path d="M7.5 9h6M7.5 12.5h6M7.5 16h3.5" />
      </svg>
    </template>

    <div class="news">
      <p v-if="failed.length" class="failed">
        <span class="failed__mark" aria-hidden="true">!</span>
        Ohne {{ failed.map((feed) => feed.name).join(', ') }} — Feed nicht erreichbar
      </p>

      <ul v-if="items.length" class="list">
        <li v-for="(item, index) in items" :key="item.link" class="item">
          <a class="item__link" :href="item.link" target="_blank" rel="noopener">
            <span class="item__index num">{{ String(index + 1).padStart(2, '0') }}</span>

            <span class="item__text">
              <span class="item__title">{{ item.title }}</span>
              <span class="item__meta">
                <span class="item__source">{{ item.source }}</span>
                <span class="item__dot" aria-hidden="true">·</span>
                <span class="item__time num">
                  <span v-if="isFresh(item)" class="item__fresh" aria-hidden="true"></span>
                  {{ item.publishedAt ? formatRelative(item.publishedAt, now) : 'ohne Datum' }}
                </span>
              </span>
            </span>

            <svg class="item__arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M7 17 17 7M8 7h9v9" />
            </svg>
          </a>
        </li>
      </ul>

      <p v-else-if="!failed.length" class="empty">Keine Meldungen.</p>
    </div>
  </WidgetCard>
</template>

<style scoped>
.list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.item + .item {
  border-top: 1px solid var(--line-faint);
}

.item__link {
  display: flex;
  align-items: flex-start;
  gap: 0.7rem;
  padding: 0.6rem 0.5rem 0.6rem 0.1rem;
  margin: 0 -0.5rem 0 -0.1rem;
  border-radius: var(--radius-sm);
  color: var(--text);
  text-decoration: none;
  transition: background 0.25s var(--ease), transform 0.25s var(--ease);
}

@media (hover: hover) {
  .item__link:hover {
    background: color-mix(in srgb, var(--surface-raised) 70%, transparent);
    transform: translateX(2px);
  }
}

/* Die laufende Nummer gibt der Liste einen Anker links und macht den Block
   ohne zusaetzliche Linien als Rangfolge lesbar. */
.item__index {
  flex: none;
  width: 1.35rem;
  padding-top: 0.15rem;
  font-size: 0.7rem;
  color: var(--text-faint);
  transition: color 0.25s var(--ease);
}

.item__link:hover .item__index,
.item__link:focus-visible .item__index {
  color: var(--accent);
}

.item__text {
  display: flex;
  flex-direction: column;
  gap: 0.22rem;
  min-width: 0;
}

.item__title {
  font-size: 0.92rem;
  line-height: 1.4;
  overflow-wrap: anywhere;
}

.item__link:hover .item__title {
  color: var(--accent-soft);
}

.item__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.35rem;
  font-size: 0.7rem;
  color: var(--text-faint);
}

.item__source {
  font-family: var(--font-mono);
  font-size: 0.66rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.item__dot {
  opacity: 0.5;
}

.item__time {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}

.item__fresh {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--cyan);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--cyan) 18%, transparent);
}

.item__arrow {
  flex: none;
  width: 13px;
  height: 13px;
  margin: 0.25rem 0 0 auto;
  color: var(--text-faint);
  opacity: 0;
  transform: translate(-3px, 3px);
  transition: opacity 0.25s var(--ease), transform 0.25s var(--ease);
}

.item__link:hover .item__arrow,
.item__link:focus-visible .item__arrow {
  opacity: 1;
  transform: none;
  color: var(--accent);
}

.failed {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  margin: 0 0 0.5rem;
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

.empty {
  margin: 0;
  color: var(--text-muted);
}
</style>
