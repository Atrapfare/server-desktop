import { computed, onUnmounted, reactive, ref } from 'vue'

const RECONNECT_BASE_MS = 1_000
const RECONNECT_MAX_MS = 30_000
const POLL_INTERVAL_MS = 60_000
const POLL_AFTER_FAILURES = 3

const widgets = reactive({})
const loading = ref(true)
const fetchError = ref(null)
const connected = ref(false)
const polling = ref(false)

// Gemeinsamer Zeittakt fuer alle relativen Zeitangaben. Ein Intervall fuer die
// ganze Seite statt eines je Kachel.
const now = ref(Date.now())

let tickHandle = null
let consumers = 0
let source = null
let reconnectHandle = null
let reconnectDelay = RECONNECT_BASE_MS
let failures = 0
let pollHandle = null

function applyPayload(payload) {
  widgets[payload.id] = payload
}

async function loadAll() {
  try {
    const response = await fetch('/api/widgets')
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }
    const data = await response.json()
    Object.values(data).forEach(applyPayload)
    fetchError.value = null
  }
  catch (error) {
    fetchError.value = error.message
  }
  finally {
    loading.value = false
  }
}

function startPolling() {
  if (pollHandle) {
    return
  }
  polling.value = true
  pollHandle = setInterval(loadAll, POLL_INTERVAL_MS)
}

function stopPolling() {
  if (pollHandle) {
    clearInterval(pollHandle)
    pollHandle = null
  }
  polling.value = false
}

function connect() {
  source = new EventSource('/api/stream')

  source.onopen = () => {
    connected.value = true
    failures = 0
    reconnectDelay = RECONNECT_BASE_MS
    stopPolling()
    // Waehrend der Verbindungsluecke verpasste Aktualisierungen nachholen.
    loadAll()
  }

  source.addEventListener('widget', (event) => {
    applyPayload(JSON.parse(event.data))
    loading.value = false
    fetchError.value = null
  })

  source.onerror = () => {
    connected.value = false
    source.close()
    source = null
    failures += 1

    // Nach mehreren Fehlschlaegen laeuft Polling als Rueckfallebene mit, waehrend
    // der Reconnect im Hintergrund weiter versucht wird.
    if (failures >= POLL_AFTER_FAILURES) {
      startPolling()
      loadAll()
    }

    reconnectHandle = setTimeout(connect, reconnectDelay)
    reconnectDelay = Math.min(reconnectDelay * 2, RECONNECT_MAX_MS)
  }
}

function disconnect() {
  clearTimeout(reconnectHandle)
  reconnectHandle = null
  stopPolling()
  if (source) {
    source.close()
    source = null
  }
  connected.value = false
}

const summary = computed(() => {
  const values = Object.values(widgets)
  const counts = { OK: 0, STALE: 0, ERROR: 0 }
  let newest = null
  for (const payload of values) {
    if (payload.status in counts) {
      counts[payload.status] += 1
    }
    if (payload.lastUpdated && (!newest || payload.lastUpdated > newest)) {
      newest = payload.lastUpdated
    }
  }
  return { total: values.length, ok: counts.OK, stale: counts.STALE, error: counts.ERROR, newest }
})

export function formatClock(isoTimestamp) {
  if (!isoTimestamp) {
    return '—'
  }
  return new Date(isoTimestamp).toLocaleTimeString('de-DE', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

export function formatRelative(isoTimestamp, reference) {
  if (!isoTimestamp) {
    return 'nie'
  }
  const seconds = Math.round((reference - new Date(isoTimestamp).getTime()) / 1000)
  if (seconds < 60) return 'gerade eben'
  const minutes = Math.round(seconds / 60)
  if (minutes < 60) return `vor ${minutes} Min.`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `vor ${hours} Std.`
  const days = Math.round(hours / 24)
  return `vor ${days} ${days === 1 ? 'Tag' : 'Tagen'}`
}

export function useDashboard() {
  consumers += 1
  if (consumers === 1) {
    loadAll()
    connect()
    tickHandle = setInterval(() => {
      now.value = Date.now()
    }, 10_000)
  }

  onUnmounted(() => {
    consumers -= 1
    if (consumers === 0) {
      clearInterval(tickHandle)
      tickHandle = null
      disconnect()
    }
  })

  return { widgets, loading, fetchError, connected, polling, summary, now, reload: loadAll }
}
