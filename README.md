# Dashboard

Selbstgehostetes Dashboard für das lokale Netz: Wetter, heutige Termine,
Nachrichten und der Zustand eines externen VPS auf einer Seite.

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
restrict,permitopen="127.0.0.1:9100",command="/usr/sbin/nologin" ssh-ed25519 AAAA... dashboard-tunnel
```

`restrict` schaltet alles ab, `permitopen` erlaubt exakt die eine
Weiterleitung. Selbst wenn der Schlüssel abhandenkommt, öffnet er nichts
weiter als diesen einen Port.

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
  -L 127.0.0.1:9100:127.0.0.1:9100 \
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

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now dashboard-tunnel
curl -s http://localhost:9100/stats
```

Bricht der Tunnel ab, wechselt die VPS-Kachel innerhalb einer Minute auf
„nicht erreichbar"; die zuletzt bekannten Werte bleiben gedimmt sichtbar.
