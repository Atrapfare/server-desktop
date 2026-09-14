# Dashboard

Selbstgehostetes Dashboard für das lokale Netz: Abfahrten, Wetter, Termine,
Nachrichten und der Zustand zweier Server auf einer Seite. Spring Boot
liefert API und Oberfläche aus einem einzigen Jar auf einem Port aus, das
Frontend ist Vue 3. Es gibt keine Datenbank — alle Daten sind jederzeit neu
abrufbar und liegen nur im Speicher.

Aktualisierungen erreichen die Seite über Server-Sent Events, ein Reload ist
nie nötig.

---

## Inhalt

- [Voraussetzungen](#voraussetzungen)
- [Lokale Entwicklung](#lokale-entwicklung)
- [Oberfläche](#oberfläche)
- [Deployment auf den Server](#deployment-auf-den-server)
- [Quellen](#quellen)
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

## Oberfläche

Dunkles Kachelraster ohne Breakpoints: `auto-fit` mit einer Mindestbreite von
340 px trägt Handy bis Wandmonitor. Die Kacheln sind untereinander gleich
aufgebaut — Rahmen, Titel, Statuslicht und relative Zeitangabe kommen aus
`WidgetCard`, die Darstellung der Werte aus dem jeweiligen Widget.

| Baustein | Ort | Zweck |
|---|---|---|
| Farben, Abstände, Schriften | `frontend/src/style.css` | Alles als CSS-Variablen auf `:root`; Widgets greifen nur darauf zu und definieren keine eigenen Farben. |
| Schriften | `@fontsource*`-Pakete | Bricolage Grotesque für Text, IBM Plex Mono für alle Zahlen. Sie werden mitgebaut statt von einem CDN geladen, damit die Seite auch ohne Weg nach draußen vollständig ist. |
| Symbole | inline im jeweiligen Widget | SVG direkt im Template, keine Icon-Bibliothek. |
| Rechnerwerte | `SystemWidget.vue` | Heimserver und VPS teilen sich eine Kachel; `title` und `place` unterscheiden sie. Fehlende Werte — etwa Lastmittel außerhalb von Linux — lässt sie weg, statt Nullen zu zeigen. |
| Favicon und App-Icons | `frontend/public/` | `favicon.svg` ist die Quelle; die PNG-Größen daneben bedienen iOS und den Homescreen über `site.webmanifest`. |

Lange Listen werden geblättert statt gescrollt: die Nachrichten zeigen fünf
Meldungen je Seite, die laufende Nummer zählt über die Seiten hinweg weiter.
Kommt im Hintergrund eine kürzere Liste an, rückt die Anzeige auf die letzte
noch vorhandene Seite, damit die Kachel nicht leer wird.

Der Zustand einer Quelle wird dreifach gezeigt, damit er auch aus einigen
Metern Entfernung lesbar bleibt: als Lichtstreifen auf der Oberkante der
Kachel, als Punkt neben der Zeitangabe und gezählt in der Kopfzeile.

Bewegung bleibt sparsam und respektiert `prefers-reduced-motion`: einmaliges
Einblenden beim Laden, ein ruhiger Puls am Live-Punkt, sonst nur Übergänge auf
Werten, die sich tatsächlich ändern.

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
    # Port 80 statt 8080, und ohne feste Adresse. Beides mit Absicht:
    #
    # Auf 8080 haelt bereits Docker die Bindung 127.0.0.1:8080 - ein
    # Wildcard-Bind auf denselben Port scheitert dort mit "Address already
    # in use".
    #
    # Und eine feste Adresse in dieser Zeile zwingt nginx, beim Start auf
    # genau diese IP zu binden. Haengt der Server am WLAN, ist sie beim
    # Booten noch nicht da: der Dienst scheitert mit "Cannot assign
    # requested address" und bleibt tot, bis jemand ihn von Hand startet.
    listen 80;
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

Erreichbar ist das Dashboard damit unter `http://<Server-IP>/`.

Soll ohne Proxy direkt aus dem LAN zugegriffen werden, ist die Zeile unter
`ports:` in der `compose.yml` auf `"8080:8080"` zu ändern. Dann hängt der
Dienst allerdings ohne jede Zwischenstufe im Netz.

### Aktualisieren

```bash
git pull && docker compose up -d --build
```

---

## Quellen

| Kachel | Woher | Intervall | Anmerkung |
|---|---|---|---|
| Fahrplan | VVS über die EFA-Schnittstelle (`XML_TRIP_REQUEST2`) | 2 min | Verbindungsauskunft, kein Abfahrtsmonitor |
| Wetter | Open-Meteo | 30 min | ohne Schlüssel |
| Termine | iCal-Adressen beliebig vieler Kalender | 15 min | faellt einer aus, laufen die uebrigen weiter |
| Nachrichten | RSS/Atom der eingetragenen Feeds | 1 h | fällt ein Feed aus, laufen die übrigen weiter |
| Heimserver | `/proc` des eigenen Rechners | 15 s | |
| VPS | Agent am lokalen Ende des SSH-Tunnels | 30 s | |

### Termine

Mehrere Kalender werden nebeneinander abgerufen und zu einer nach Beginn
sortierten Liste zusammengefuehrt. Der Name des Kalenders steht an den
Terminen, sobald mehr als einer Termine liefert. Faellt einer aus, laufen die
uebrigen weiter und der Ausfall wird in der Kachel vermerkt — wie bei den
Nachrichten.

Der Hauptkalender steht in `CALENDAR_ICAL_URL`, weitere kommen fortlaufend ab
0 nummeriert dazu:

```bash
DASHBOARD_CALENDAR_SOURCES_0_NAME=Uni
DASHBOARD_CALENDAR_SOURCES_0_URL=https://…
DASHBOARD_CALENDAR_SOURCES_1_NAME=Familie
DASHBOARD_CALENDAR_SOURCES_1_URL=https://…
```

Eine Luecke in der Nummerierung beendet die Liste.

Die Adresse eines Google-Kalenders steht in dessen Einstellungen unter
*Kalender integrieren → Geheime Adresse im iCal-Format*. Sie ist das
Geheimnis selbst: wer sie kennt, liest den Kalender. Fuer Kalender, die
jemand anderes nur freigegeben hat, bietet Google sie nicht an — dort fuehrt
der Weg ueber die urspruengliche Quelle, etwa den iCal-Link aus ILIAS.

Abgerufen wird mit `Accept-Encoding: gzip`. iCal ist Text und schrumpft
stark — beim Google-Kalender von 464 auf 90 kB. Auf einer schwachen Leitung
entscheidet die Menge der Bytes darueber, ob der Abruf durchkommt.

### Fahrplan

Abgefragt wird die Verbindungsauskunft und nicht der Abfahrtsmonitor. An
einer Haltestelle fährt dieselbe Linie in beide Richtungen — welche Abfahrt
tatsächlich ans Ziel führt, weiß erst die Auskunft. Sie trägt außerdem einen
Umstieg oder einen Fußweg mit, falls der Fahrplan das eines Tages verlangt;
am Abend und nachts ist das auf der voreingestellten Strecke bereits der Fall.

Die Haltestellen stehen als IDs in der Konfiguration. Die eigene findet man
über den Stopfinder derselben Schnittstelle:

```bash
curl "https://www3.vvs.de/vvs/XML_STOPFINDER_REQUEST?outputFormat=rapidJSON&type_sf=any&name_sf=Laihle"
```

Die Kachel zeigt die nächste Verbindung groß mit Countdown und darunter die
folgenden. Beginnt eine Verbindung mit einem Fußweg, ist die genannte Zeit
der Aufbruch von zu Hause, nicht die Abfahrt des Fahrzeugs — die Kachel sagt
das dann dazu. Der Countdown läuft im Browser sekundenweise weiter, unabhängig
vom Abrufintervall.

### Heimserver

Gelesen wird direkt aus `/proc`, genau wie es der VPS-Agent auf der Gegenseite
tut. Im Container ist das der richtige Weg: Last, Speicher und Laufzeit sind
im Kernel nicht pro Container getrennt, `/proc` zeigt dort also bereits die
Werte des Hosts. Nur das Dateisystem ist getrennt — deshalb hängt die
`compose.yml` die Wurzel des Hosts unter `/hostfs` nur lesbar ein und
`HOST_DISK_PATH` zeigt darauf. Wer das nicht möchte, entfernt beides; dann
meldet die Kachel die Platte des Containers.

Fehlt `/proc` ganz — etwa bei der Entwicklung unter Windows — treten die Werte
der JVM an seine Stelle. Laufzeit und Lastmittel lassen sich so nicht
bestimmen und bleiben leer, statt geraten zu werden; die Kachel lässt die
Zeile dann weg.

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
die Darstellung von `STALE` und `ERROR`. Über den Slot `icon` nimmt sie ein
kleines SVG für die Kopfzeile entgegen.

```vue
<script setup>
import WidgetCard from './WidgetCard.vue'

const props = defineProps({ payload: { type: Object, default: null } })
</script>

<template>
  <WidgetCard title="Strompreis" :payload="payload">
    <template #icon>
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M13 3 5 14h6l-1 7 8-11h-6z" />
      </svg>
    </template>

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

Scheitert ein Abruf, folgt nach einer Minute genau ein zweiter Anlauf —
aber nur bei Intervallen über drei Minuten. Bei kurzen Intervallen kommt der
reguläre Versuch ohnehin gleich, da wäre die Wiederholung nur zusätzliche
Last. Ohne sie stünden die Nachrichten nach einem einzigen Fehlschlag eine
volle Stunde leer, bis das nächste Intervall fällig wird.

Ein `collect()`, das länger als 30 Sekunden braucht, wird abgebrochen und
zählt als Fehlschlag. Ein hängender Aufruf hält die übrigen Quellen nicht auf.

---

## Umgebungsvariablen

Secrets stehen ausschließlich in `.env`, nie in der `application.yml`. `.env`
ist über `.gitignore` ausgeschlossen, `.env.example` listet alles auf.

| Variable | Pflicht | Standard | Bedeutung |
|---|---|---|---|
| `CALENDAR_ICAL_URL` | ja | — | Geheime iCal-Adresse des Kalenders. Fehlt sie, meldet nur die Termin-Kachel einen Fehler; die Anwendung startet trotzdem. |
| `VPS_STATS_URL` | nein | `http://localhost:9100/stats` | Lokales Ende des SSH-Tunnels. Im Container `http://host.docker.internal:9100/stats`. |
| `HOST_NAME` | nein | Rechnername | Anzeigename in der Heimserver-Kachel. Im Container wäre der Rechnername sonst die Container-ID. |
| `HOST_DISK_PATH` | nein | `/` | Dateisystem, dessen Belegung gemeldet wird. Im Container `/hostfs`, passend zum Bind-Mount der `compose.yml`. |
| `TRANSIT_ORIGIN` | nein | `de:08111:2420` | VVS-Haltestelle der Abfahrt (Stuttgart, Laihle). |
| `TRANSIT_DESTINATION` | nein | `de:08111:6008` | VVS-Haltestelle des Ziels (Stuttgart, Universität). |
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
