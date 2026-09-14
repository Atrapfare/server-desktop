#!/usr/bin/env bash
#
# uninstall-dk-server.sh — macht alle Aenderungen rueckgaengig, die fuer das
# Dashboard-Projekt auf dem Heimserver vorgenommen wurden.
#
# Aufruf:  sudo bash uninstall-dk-server.sh [OPTIONEN]
#
#   --yes           ohne Rueckfrage ausfuehren
#   --purge-nginx   nginx nicht nur konfigurieren, sondern deinstallieren
#   --purge-autossh autossh deinstallieren
#   --keep-repo     das geklonte Repository stehen lassen
#
# Wird entfernt:
#   - Docker-Container, Image, Netz und Volumes des Dashboards
#   - geklontes Repository samt .env
#   - systemd-Dienst dashboard-tunnel
#   - Systemnutzer dashboard-tunnel samt Schluessel
#   - nginx-Site "dashboard" (Paket nur mit --purge-nginx)
#   - die beiden UFW-Regeln fuer Port 8080 und 9100
#
# NICHT angetastet:
#   - Docker selbst und andere Container
#   - die Systemhaertung aus dem Audit (sysctl, journald, cloud-init,
#     deaktivierte Dienste, SSH-Konfiguration) - die ist unabhaengig
#     vom Dashboard und bleibt bestehen
#   - dein eigener Nutzer und dessen SSH-Zugang
#   - WLAN- und Netzwerkkonfiguration

set -u

YES=0
PURGE_NGINX=0
PURGE_AUTOSSH=0
KEEP_REPO=0

for arg in "$@"; do
    case "$arg" in
        --yes)           YES=1 ;;
        --purge-nginx)   PURGE_NGINX=1 ;;
        --purge-autossh) PURGE_AUTOSSH=1 ;;
        --keep-repo)     KEEP_REPO=1 ;;
        *) echo "Unbekannte Option: $arg"; exit 1 ;;
    esac
done

if [ "$(id -u)" -ne 0 ]; then
    echo "Bitte mit sudo ausfuehren:  sudo bash $0"
    exit 1
fi

TARGET_USER="${SUDO_USER:-root}"
TARGET_HOME=$(getent passwd "$TARGET_USER" | cut -d: -f6)
REPO_DIR="${TARGET_HOME}/server-desktop"

DONE=(); SKIPPED=(); FAILED=()

ok()   { DONE+=("$1");    printf '  [entfernt]     %s\n' "$1"; }
skip() { SKIPPED+=("$1"); printf '  [nicht da]     %s\n' "$1"; }
bad()  { FAILED+=("$1");  printf '  [FEHLER]       %s\n' "$1"; }

# ─────────────────────────────────────────────────────────────
echo ""
echo "═══════════════════════════════════════════════════"
echo "  Dashboard-Installation auf dem Heimserver entfernen"
echo "═══════════════════════════════════════════════════"
echo ""
echo "Host:     $(hostname)"
echo "Betrifft: Docker-Stack, SSH-Tunnel, nginx-Site, UFW-Regeln"
echo ""
echo "ACHTUNG: ${REPO_DIR}/.env enthaelt deine private iCal-URL und"
echo "wird mitgeloescht. Nicht committete Aenderungen am Code gehen"
echo "ebenfalls verloren."
[ "$KEEP_REPO" -eq 1 ] && echo "(--keep-repo gesetzt: Repository bleibt erhalten)"
echo ""

if [ "$YES" -eq 0 ]; then
    printf 'Fortfahren? Tippe ENTFERNEN: '
    read -r answer
    [ "$answer" = "ENTFERNEN" ] || { echo "Abgebrochen."; exit 0; }
fi
echo ""

# ── 1. Docker ────────────────────────────────────────────────
echo "1. Docker"

if [ -d "$REPO_DIR" ] && [ -f "${REPO_DIR}/compose.yml" ]; then
    if (cd "$REPO_DIR" && docker compose down --volumes --remove-orphans >/dev/null 2>&1); then
        ok "Container, Netz und Volumes gestoppt und entfernt"
    else
        bad "docker compose down fehlgeschlagen"
    fi
else
    skip "compose.yml (Stack wird direkt aufgeraeumt)"
    docker rm -f dashboard >/dev/null 2>&1 && ok "Container dashboard"
    docker network rm dashboard >/dev/null 2>&1 && ok "Netz dashboard"
fi

if docker image inspect dashboard:latest >/dev/null 2>&1; then
    docker image rm -f dashboard:latest >/dev/null 2>&1 \
        && ok "Image dashboard:latest" || bad "Image dashboard:latest"
else
    skip "Image dashboard:latest"
fi

# Build-Cache des Projekts - befreit auf der kleinen SSD spuerbar Platz.
docker builder prune -f >/dev/null 2>&1 && ok "Docker-Build-Cache geleert"

# ── 2. SSH-Tunnel ────────────────────────────────────────────
echo ""
echo "2. SSH-Tunnel"

if systemctl list-unit-files 2>/dev/null | grep -q '^dashboard-tunnel.service'; then
    systemctl disable --now dashboard-tunnel >/dev/null 2>&1 \
        && ok "Dienst dashboard-tunnel gestoppt und deaktiviert" \
        || bad "Dienst dashboard-tunnel liess sich nicht stoppen"
else
    skip "Dienst dashboard-tunnel"
fi

if [ -f /etc/systemd/system/dashboard-tunnel.service ]; then
    rm -f /etc/systemd/system/dashboard-tunnel.service \
        && ok "/etc/systemd/system/dashboard-tunnel.service" \
        || bad "Unit-Datei liess sich nicht loeschen"
    systemctl daemon-reload
    systemctl reset-failed dashboard-tunnel >/dev/null 2>&1 || true
else
    skip "Unit-Datei dashboard-tunnel.service"
fi

# Reste von autossh, falls der Dienst hart beendet wurde
pkill -f 'autossh.*9100' >/dev/null 2>&1 && ok "verwaiste autossh-Prozesse"

# ── 3. Nutzer ────────────────────────────────────────────────
echo ""
echo "3. Nutzer"

if [ "$TARGET_USER" = "dashboard-tunnel" ]; then
    bad "Skript laeuft als dashboard-tunnel - Nutzer wird nicht entfernt"
elif getent passwd dashboard-tunnel >/dev/null; then
    pkill -KILL -u dashboard-tunnel 2>/dev/null || true
    sleep 1
    if userdel -r dashboard-tunnel 2>/dev/null; then
        ok "Nutzer dashboard-tunnel samt Schluesseln"
    elif userdel dashboard-tunnel 2>/dev/null; then
        ok "Nutzer dashboard-tunnel"
    else
        bad "Nutzer dashboard-tunnel liess sich nicht entfernen"
    fi
else
    skip "Nutzer dashboard-tunnel"
fi

[ -d /var/lib/dashboard-tunnel ] && {
    rm -rf /var/lib/dashboard-tunnel && ok "/var/lib/dashboard-tunnel/"
}

# ── 4. nginx ─────────────────────────────────────────────────
echo ""
echo "4. nginx"

SITE_REMOVED=0
[ -L /etc/nginx/sites-enabled/dashboard ] && {
    rm -f /etc/nginx/sites-enabled/dashboard && ok "Site-Symlink dashboard" && SITE_REMOVED=1
}
[ -f /etc/nginx/sites-available/dashboard ] && {
    rm -f /etc/nginx/sites-available/dashboard && ok "Site-Datei dashboard" && SITE_REMOVED=1
}
[ "$SITE_REMOVED" -eq 0 ] && skip "nginx-Site dashboard"

if [ "$PURGE_NGINX" -eq 1 ]; then
    systemctl disable --now nginx >/dev/null 2>&1
    DEBIAN_FRONTEND=noninteractive apt-get purge -y nginx nginx-common >/dev/null 2>&1 \
        && ok "Pakete nginx und nginx-common entfernt" \
        || bad "nginx liess sich nicht deinstallieren"
    DEBIAN_FRONTEND=noninteractive apt-get autoremove -y >/dev/null 2>&1
elif systemctl is-active nginx >/dev/null 2>&1; then
    # Ohne Site und ohne Default-Site wuerde nginx beim Reload scheitern.
    if [ -z "$(ls -A /etc/nginx/sites-enabled 2>/dev/null)" ]; then
        systemctl stop nginx >/dev/null 2>&1
        ok "nginx gestoppt (keine Site mehr aktiv)"
        echo "                 Paket bleibt installiert - mit --purge-nginx entfernen"
    else
        systemctl reload nginx >/dev/null 2>&1 \
            && ok "nginx neu geladen (weitere Sites aktiv, laeuft weiter)" \
            || bad "nginx-Reload fehlgeschlagen"
    fi
fi

# ── 5. Firewall ──────────────────────────────────────────────
echo ""
echo "5. UFW-Regeln"

if command -v ufw >/dev/null 2>&1; then
    if ufw delete allow from 172.22.22.0/24 to any port 8080 proto tcp >/dev/null 2>&1; then
        ok "Regel: 8080/tcp aus 172.22.22.0/24"
    else
        skip "Regel: 8080/tcp aus 172.22.22.0/24"
    fi
    if ufw delete allow from 172.28.0.0/24 to 172.28.0.1 port 9100 proto tcp >/dev/null 2>&1; then
        ok "Regel: 9100/tcp aus dem Docker-Netz"
    else
        skip "Regel: 9100/tcp aus dem Docker-Netz"
    fi
else
    skip "UFW nicht vorhanden"
fi

# ── 6. autossh ───────────────────────────────────────────────
echo ""
echo "6. Pakete"

if [ "$PURGE_AUTOSSH" -eq 1 ]; then
    if dpkg -l autossh 2>/dev/null | grep -q '^ii'; then
        DEBIAN_FRONTEND=noninteractive apt-get purge -y autossh >/dev/null 2>&1 \
            && ok "Paket autossh entfernt" || bad "autossh liess sich nicht entfernen"
    else
        skip "Paket autossh"
    fi
else
    skip "autossh (mit --purge-autossh entfernen)"
fi

# ── 7. Repository ────────────────────────────────────────────
echo ""
echo "7. Repository"

if [ "$KEEP_REPO" -eq 1 ]; then
    skip "Repository (--keep-repo)"
elif [ -d "$REPO_DIR" ]; then
    rm -rf "$REPO_DIR" && ok "$REPO_DIR (inklusive .env)" || bad "$REPO_DIR"
else
    skip "$REPO_DIR"
fi

# ── 8. Kontrolle ─────────────────────────────────────────────
echo ""
echo "8. Kontrolle"

ss -tlpn 2>/dev/null | grep -q ':8080' \
    && bad "Es lauscht weiterhin etwas auf Port 8080" \
    || ok "Port 8080 ist frei"

ss -tlpn 2>/dev/null | grep -q ':9100' \
    && bad "Es lauscht weiterhin etwas auf Port 9100" \
    || ok "Port 9100 ist frei"

ip -br a 2>/dev/null | grep -q '172.28' \
    && bad "Docker-Netz 172.28.0.0/24 existiert noch" \
    || ok "Docker-Netz entfernt"

getent passwd dashboard-tunnel >/dev/null && bad "Nutzer dashboard-tunnel existiert noch"

# ── Zusammenfassung ──────────────────────────────────────────
echo ""
echo "═══════════════════════════════════════════════"
printf "  Entfernt: %s   Nicht vorhanden: %s   Fehler: %s\n" \
    "${#DONE[@]}" "${#SKIPPED[@]}" "${#FAILED[@]}"
echo "═══════════════════════════════════════════════"

if [ "${#FAILED[@]}" -gt 0 ]; then
    echo ""
    echo "Nicht erledigt:"
    printf '  - %s\n' "${FAILED[@]}"
fi

echo ""
echo "Bewusst nicht angetastet:"
echo "  - Systemhaertung (sysctl, journald, SSH, deaktivierte Dienste)"
echo "  - Docker selbst, WLAN, Firewall-Grundkonfiguration"
echo ""
echo "Nicht vergessen:"
echo "  - Auf dem VPS separat 'uninstall-vps.sh' ausfuehren"
echo "  - Falls du die private iCal-URL nicht mehr brauchst: in den"
echo "    Google-Kalender-Einstellungen zuruecksetzen"
echo "  - Gespeicherter GitHub-Token: ~/.git-credentials"
echo ""
