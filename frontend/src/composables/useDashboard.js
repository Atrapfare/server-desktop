import { onUnmounted, reactive, ref } from 'vue'

const widgets = reactive({})
const loading = ref(true)
const fetchError = ref(null)

// Gemeinsamer Zeittakt fuer alle relativen Zeitangaben. Ein Intervall fuer die
// ganze Seite statt eines je Kachel.
const now = ref(Date.now())
let tickHandle = null
let consumers = 0

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

export function formatRelative(isoTimestamp, reference) {
  if (!isoTimestamp) {
    return 'nie'
  }
  const seconds = Math.round((reference - new Date(isoTimestamp).getTime()) / 1000)
  if (seconds < 0) return 'gerade eben'
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
    tickHandle = setInterval(() => {
      now.value = Date.now()
    }, 10_000)
  }

  onUnmounted(() => {
    consumers -= 1
    if (consumers === 0) {
      clearInterval(tickHandle)
      tickHandle = null
    }
  })

  return { widgets, loading, fetchError, now, reload: loadAll }
}
