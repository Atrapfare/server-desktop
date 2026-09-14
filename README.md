# Dashboard

Selbstgehostetes Dashboard für das lokale Netz: Wetter, Termine,
Nachrichten und der Zustand eines externen VPS auf einer Seite. Spring Boot
liefert API und Oberfläche aus einem einzigen Jar auf einem Port aus, das
Frontend ist Vue 3. Es gibt keine Datenbank — alle Daten sind jederzeit neu
abrufbar und liegen nur im Speicher.

Aktualisierungen erreichen die Seite über Server-Sent Events, ein Reload ist
nie nötig.

---

## Inhalt

- [Voraussetzungen](#voraussetzungen)
- [Lokale Entwicklung](#lokale-entwicklung)
- [Deployment auf den Server](#deployment-auf-den-server)
- [Eine neue Datenquelle hinzufügen](#eine-neue-datenquelle-hinzufügen)
- [Umgebungsvariablen](#umgebungsvariablen)
- [API](#api)
- [VPS-Agent](#vps-agent)
- [SSH-Tunnel vom Heimserver](#ssh-tunnel-vom-heimserver)

---

## Voraussetzungen

| Zweck | Bedarf |
|---|---|
| Bauen | JDK 21 (LTS). Maven kommt über den Wrapper, Node und npm holt das `frontend-maven-plugin` selbst. |
| Betrieb | Docker mit Compose-Plugin. Sonst nichts. |
| VPS-Agent | Python 3 aus der Distribution, keine Pakete darüber hinaus. |

Die Java-Version steht in der `pom.xml` unter `java.version`, Node und npm
darunter. Für einen Wechsel auf eine neuere LTS ändert sich dort eine Zeile,
dazu die beiden Basis-Images im `Dockerfile`.

---

## Lokale Entwicklung

Backend und Vite laufen getrennt; Vite reicht `/api` an Port 8080 durch, so
dass Hot Reload im Frontend und ein Neustart des Backends sich nicht in die
Quere kommen.

```bash
cp .env.example .env      # CALENDAR_ICAL_URL eintragen
```

Terminal 1 — Backend mit verkürzten Intervallen:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev -Dfrontend.skip=true
```

Terminal 2 — Vite:

```bash
cd frontend && npm install && npm run dev
```

Die Oberfläche liegt dann auf `http://localhost:5173`.

Das Profil `dev` setzt alle Abrufintervalle auf eine Minute und hebt das
Logging auf `DEBUG`. `-Dfrontend.skip=true` überspringt den Vue-Build im
Maven-Lauf — den übernimmt in Terminal 2 ohnehin Vite.

### Gesamtes Artefakt bauen

```bash
./mvnw clean package
java -jar target/dashboard-0.0.1-SNAPSHOT.jar
```

Dann liegt alles auf `http://localhost:8080`. Das `frontend-maven-plugin` baut
Vue in der Phase `generate-resources` nach `src/main/resources/static/`; das
Verzeichnis gehört deshalb nicht ins Repo.

### Tests

```bash
./mvnw test -Dfrontend.skip=true
```

Abgedeckt sind das Zustandsverhalten der `CollectorRegistry` — vor allem der
Übergang nach `STALE` — und die RRULE-Expansion des Kalenders gegen einen
Beispielkalender. Für HTTP-Aufrufe nach außen gibt es bewusst keine Tests.

---

## Deployment auf den Server

```bash
git clone <repo> dashboard && cd dashboard
cp .env.example .env        # ausfüllen
docker compose up -d
```

Der Build läuft im Container ab, auf dem Server muss kein JDK liegen. Auf dem
Mac Mini dauert der erste Durchlauf einige Minuten, danach greifen die
Docker-Schichten.

```bash
docker compose ps           # muss "healthy" zeigen
docker compose logs -f
curl -s http://127.0.0.1:8080/api/health
```

`HEALTHCHECK` fragt alle 30 Sekunden `/api/health` ab, mit 90 Sekunden
Anlaufzeit für den Start auf schwacher Hardware. Der Container läuft als
unprivilegierter Nutzer mit schreibgeschütztem Dateisystem.

### Zugriff aus dem WLAN

Der Container ist an `127.0.0.1:8080` des Hosts gebunden, nicht an `0.0.0.0`
— aus dem WLAN ist er damit **nicht** direkt erreichbar. Das ist Absicht:
davor gehört ein Reverse Proxy, der auf der LAN-Adresse lauscht.

nginx auf dem Heimserver:

```nginx
server {
    listen 172.22.22.183:8080;
    server_name _;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;

        # Ohne diese Zeilen sammelt nginx den SSE-Strom im Puffer und gibt
        # ihn nur verzögert weiter - die Seite bliebe scheinbar stehen.
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 1h;
    }
}
```

Soll ohne Proxy direkt aus dem LAN zugegriffen werden, ist die Zeile unter
`ports:` in der `compose.yml` auf `"8080:8080"` zu ändern. Dann hängt der
Dienst allerdings ohne jede Zwischenstufe im Netz.

### Aktualisieren

```bash
git pull && docker compose up -d --build
```

---

## Eine neue Datenquelle hinzufügen

Das Projekt ist darauf ausgelegt zu wachsen. Eine neue Quelle ist **eine
einzige Klasse**, die `Collector` implementiert — Zeitplanung, Timeout,
Fehlerbehandlung, Auslieferung über `/api/widgets` und der Versand über SSE
kommen von allein. Braucht eine Quelle mehr als diese eine Klasse, stimmt
etwas mit der Abstraktion nicht.

### 1. Datenrecord und Collector

`src/main/java/com/atrapfare/dashboard/sources/strom/StromData.java`:

```java
public record StromData(double centPerKwh, String tendenz) {
}
```

`src/main/java/com/atrapfare/dashboard/sources/strom/StromCollector.java`:

```java
@Component
public class StromCollector implements Collector<StromData> {

    private final DashboardProperties.Strom properties;
    private final RestClient restClient;

    public StromCollector(DashboardProperties properties, RestClient.Builder builder) {
        this.properties = properties.strom();
        this.restClient = builder.build();
    }

    @Override
    public String id() {
        return "strom";          // stabiler Schlüssel, auch im Frontend
    }

    @Override
    public Duration interval() {
        return properties.interval();
    }

    @Override
    public StromData collect() {
        // Einfach werfen, wenn etwas schiefgeht - die Registry macht daraus
        // STALE und behält den letzten guten Wert.
        return restClient.get().uri(properties.url()).retrieve().body(StromData.class);
    }
}
```

Die `CollectorRegistry` bekommt alle `Collector`-Beans per Konstruktor
injiziert und nimmt den neuen ohne weiteres Zutun auf.

### 2. Konfiguration ergänzen

In `DashboardProperties` einen Abschnitt anlegen:

```java
public record Strom(String url, Duration interval) {
}
```

und im Record-Kopf als `@NestedConfigurationProperty Strom strom` aufnehmen.
Dazu in `application.yml`:

```yaml
dashboard:
  strom:
    url: https://beispiel.invalid/preise
    interval: 15m
```

Im Profil `dev` darunter ein kürzeres Intervall eintragen.

### 3. Widget

`frontend/src/components/StromWidget.vue` nach dem Muster der übrigen
Widgets: `WidgetCard` als Rahmen, die eigentliche Darstellung als Inhalt.
`WidgetCard` kümmert sich um Titel, Statuspunkt, relative Zeitangabe sowie um
die Darstellung von `STALE` und `ERROR`.

```vue
<script setup>
import WidgetCard from './WidgetCard.vue'

const props = defineProps({ payload: { type: Object, default: null } })
</script>

<template>
  <WidgetCard title="Strompreis" :payload="payload">
    <div v-if="payload?.data">{{ payload.data.centPerKwh }} ct/kWh</div>
  </WidgetCard>
</template>
```

In `App.vue` einhängen — der Schlüssel ist die `id()` aus dem Collector:

```vue
<StromWidget :payload="widgets.strom" />
```

Mehr ist nicht nötig. Das Grid ordnet die Kachel selbst ein, und die
Statuszeile zählt die neue Quelle automatisch mit.

### Zustandsverhalten, das dabei abfällt

| Lage | Zustand | Anzeige |
|---|---|---|
| Abruf erfolgreich | `OK` | Werte samt Zeitpunkt des Abrufs |
| Abruf schlägt fehl, vorher lief einer durch | `STALE` | letzter Wert gedimmt, Hinweis, Zeitstempel des letzten Erfolgs |
| Abruf schlug noch nie zu | `ERROR` | Fehlertext statt Inhalt |

Ein `collect()`, das länger als 15 Sekunden braucht, wird abgebrochen und
zählt als Fehlschlag. Ein hängender Aufruf hält die übrigen Quellen nicht auf.

---

## Umgebungsvariablen

Secrets stehen ausschließlich in `.env`, nie in der `application.yml`. `.env`
ist über `.gitignore` ausgeschlossen, `.env.example` listet alles auf.

| Variable | Pflicht | Standard | Bedeutung |
|---|---|---|---|
| `CALENDAR_ICAL_URL` | ja | — | Geheime iCal-Adresse des Kalenders. Fehlt sie, meldet nur die Termin-Kachel einen Fehler; die Anwendung startet trotzdem. |
| `VPS_STATS_URL` | nein | `http://localhost:9100/stats` | Lokales Ende des SSH-Tunnels. Im Container `http://host.docker.internal:9100/stats`. |
| `TZ` | nein | `UTC` | Zeitzone. Die `compose.yml` setzt `Europe/Berlin` — ohne das erschienen Termine am falschen Tag. |
| `SERVER_ADDRESS` | nein | `127.0.0.1` | Bind-Adresse. Das `Dockerfile` setzt `0.0.0.0`, weil die Portfreigabe den Dienst sonst nicht erreicht; nach außen begrenzt ihn das Host-Binding der `compose.yml`. |
| `SPRING_PROFILES_ACTIVE` | nein | — | `dev` verkürzt alle Intervalle auf eine Minute. |

Jede Einstellung aus der `application.yml` lässt sich zusätzlich über eine
Umgebungsvariable übersteuern, etwa `DASHBOARD_WEATHER_LATITUDE=52.52` oder
`DASHBOARD_NEWS_MAXITEMS=25`.

---

## API

| Endpunkt | Inhalt |
|---|---|
| `GET /api/widgets` | Alle bekannten Payloads, Schlüssel ist die Collector-ID. |
| `GET /api/stream` | `text/event-stream`. Ein Event `widget` je Aktualisierung, alle 30 Sekunden ein Heartbeat-Kommentar. |
| `GET /api/health` | Je Collector ID, Zustand und Alter in Sekunden. Für Debugging ohne Browser und für den Docker-`HEALTHCHECK`. |

Das Frontend verbindet sich nach einem Abriss mit exponentiell wachsendem
Abstand neu, gedeckelt bei 30 Sekunden. Bleibt der Stream dreimal
hintereinander aus, läuft zusätzlich ein Abruf im Minutentakt, bis die
Verbindung wieder steht. Nach jedem erfolgreichen Verbindungsaufbau wird
`/api/widgets` einmal nachgeholt.

```bash
curl -N http://127.0.0.1:8080/api/stream
```

---

## VPS-Agent

Der Agent unter `agent/vps_agent.py` ist ein einzelnes Python-Skript ohne
Fremdbibliotheken. Er liest `/proc/uptime`, `/proc/loadavg`, `/proc/meminfo`
und `statvfs` für `/` und beantwortet `GET /stats`.

**Er bindet ausschließlich an `127.0.0.1:9100`.** Damit steht auf dem VPS kein
zusätzlicher Port nach außen offen; der Heimserver erreicht ihn über einen
SSH-Tunnel.

### Installation auf dem VPS

```bash
sudo useradd --system --no-create-home --shell /usr/sbin/nologin dashboard-agent
sudo install -d -o root -g root /opt/dashboard-agent
sudo install -m 0644 -o root -g root vps_agent.py /opt/dashboard-agent/
sudo install -m 0644 -o root -g root dashboard-agent.service /etc/systemd/system/

sudo systemctl daemon-reload
sudo systemctl enable --now dashboard-agent
```

Prüfen:

```bash
curl -s http://127.0.0.1:9100/stats | python3 -m json.tool
```

Von außen darf der Port **nicht** erreichbar sein — das ist Absicht:

```bash
curl --max-time 3 http://<vps-adresse>:9100/stats   # muss fehlschlagen
```

---

## SSH-Tunnel vom Heimserver

Der Heimserver hält den Tunnel dauerhaft offen und erreicht den Agenten dann
unter `http://localhost:9100/stats` — genau der Wert, der in `VPS_STATS_URL`
steht.

### Schlüssel anlegen

Auf dem **Heimserver**, als der Nutzer, unter dem der Tunnel läuft:

```bash
sudo useradd --system --create-home --home-dir /var/lib/dashboard-tunnel \
  --shell /usr/sbin/nologin dashboard-tunnel
sudo -u dashboard-tunnel ssh-keygen -t ed25519 -N "" \
  -f /var/lib/dashboard-tunnel/.ssh/id_ed25519
```

Auf dem **VPS** einen Nutzer anlegen, der nichts darf außer weiterleiten. In
`~/.ssh/authorized_keys` dieses Nutzers den öffentlichen Schlüssel mit
Einschränkungen eintragen:

```
restrict,port-forwarding,permitopen="127.0.0.1:9100",command="/usr/sbin/nologin" ssh-ed25519 AAAA... dashboard-tunnel
```

`restrict` schaltet alles ab — **auch das Port-Forwarding selbst**.
`port-forwarding` schaltet es gezielt wieder ein, `permitopen` begrenzt es
auf genau dieses eine Ziel, und `command` verhindert, dass über den Schlüssel
irgendein Befehl ausgeführt werden kann. `permitopen` allein genügt nach
`restrict` **nicht**: es schränkt nur ein, was erlaubt ist, und hebt die
Sperre nicht auf — die Verbindung stünde, aber die Weiterleitung schlüge mit
`administratively prohibited` fehl.

Selbst wenn der Schlüssel abhandenkommt, öffnet er damit nichts weiter als
diesen einen Port.

Einmal von Hand verbinden, damit der Hostschlüssel in `known_hosts` landet:

```bash
sudo -u dashboard-tunnel ssh -i /var/lib/dashboard-tunnel/.ssh/id_ed25519 \
  tunnel@<vps-adresse> -N -L 9100:127.0.0.1:9100
```

### autossh als systemd-Unit

`autossh` baut den Tunnel nach einem Abriss selbst wieder auf. Ohne ihn bliebe
eine tote SSH-Verbindung stehen, ohne dass systemd den Dienst als gescheitert
sieht.

```bash
sudo apt install autossh
```

`/etc/systemd/system/dashboard-tunnel.service`:

```ini
[Unit]
Description=SSH-Tunnel zum VPS-Agent
After=network-online.target
Wants=network-online.target

[Service]
User=dashboard-tunnel
Environment=AUTOSSH_GATETIME=0
ExecStart=/usr/bin/autossh -M 0 -N \
  -o ServerAliveInterval=30 -o ServerAliveCountMax=3 \
  -o ExitOnForwardFailure=yes -o StrictHostKeyChecking=yes \
  -i /var/lib/dashboard-tunnel/.ssh/id_ed25519 \
  -L 172.28.0.1:9100:127.0.0.1:9100 \
  tunnel@<vps-adresse>
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

`-M 0` schaltet die Monitor-Ports von autossh ab; die Erkennung übernehmen
`ServerAliveInterval` und `ServerAliveCountMax`. `ExitOnForwardFailure=yes`
sorgt dafür, dass ein belegter lokaler Port als Fehler auffällt, statt still
eine Verbindung ohne Weiterleitung zu halten.

Der Tunnel bindet an `172.28.0.1` — das Gateway des Docker-Netzes aus der
`compose.yml`. Der Container erreicht diese Adresse unter dem Namen
`host.docker.internal`; auf dem Loopback des Hosts (`127.0.0.1`) käme er
nicht an, denn dessen localhost ist der Container selbst. Die Adresse liegt
auf der Docker-Bridge und nicht im WLAN.

Läuft das Jar stattdessen direkt auf dem Host, genügt
`-L 127.0.0.1:9100:127.0.0.1:9100` und `VPS_STATS_URL=http://localhost:9100/stats`.

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now dashboard-tunnel
curl -s http://localhost:9100/stats
```

Bricht der Tunnel ab, wechselt die VPS-Kachel innerhalb einer Minute auf
„nicht erreichbar"; die zuletzt bekannten Werte bleiben gedimmt sichtbar.
