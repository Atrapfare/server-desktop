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
</script>

<template>
  <WidgetCard title="Nachrichten" :payload="payload">
    <p v-if="failed.length" class="failed">
      Ohne {{ failed.map((feed) => feed.name).join(', ') }} — Feed nicht erreichbar
    </p>

    <ul v-if="items.length" class="news">
      <li v-for="item in items" :key="item.link" class="item">
        <a class="item__link" :href="item.link" target="_blank" rel="noopener">{{ item.title }}</a>
        <span class="item__meta">
          <span class="item__source">{{ item.source }}</span>
          <span class="item__time">{{ item.publishedAt ? formatRelative(item.publishedAt, now) : 'ohne Datum' }}</span>
        </span>
      </li>
    </ul>

    <p v-else-if="!failed.length" class="empty">Keine Meldungen.</p>
  </WidgetCard>
</template>

<style scoped>
.news {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.item {
  padding: 0.5rem 0;
  min-width: 0;
}

.item + .item {
  border-top: 1px solid var(--border);
}

.item__link {
  color: var(--text);
  text-decoration: none;
  overflow-wrap: anywhere;
}

.item__link:hover,
.item__link:focus-visible {
  color: var(--accent);
  text-decoration: underline;
}

.item__meta {
  display: flex;
  gap: 0.5rem;
  align-items: baseline;
  margin-top: 0.2rem;
  font-size: 0.76rem;
  color: var(--text-faint);
}

.item__source {
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--text-muted);
}

.failed {
  margin: 0 0 0.4rem;
  font-size: 0.78rem;
  color: var(--stale);
}

.empty {
  margin: 0;
  color: var(--text-muted);
}
</style>
