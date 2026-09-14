#!/usr/bin/env bash
#
# uninstall-vps.sh — macht alle Aenderungen rueckgaengig, die fuer das
# Dashboard-Projekt auf dem VPS vorgenommen wurden.
#
# Aufruf:  sudo bash uninstall-vps.sh [OPTIONEN]
#
#   --yes              ohne Rueckfrage ausfuehren
#   --revert-hostname  Hostnamen von "vps" zurueck auf "server" setzen
#   --keep-repo        das geklonte Repository stehen lassen
#
# Wird entfernt:
#   - systemd-Dienst dashboard-agent
#   - /opt/dashboard-agent/
#   - Systemnutzer dashboard-agent
#   - Nutzer tunnel samt Home und authorized_keys
#   - geklontes Repository im Home des aufrufenden Nutzers
#
# NICHT angetastet:
#   - dein eigener Nutzer und dessen SSH-Zugang
#   - Systemhaertung, Firewall, Pakete
#   - alles, was nichts mit dem Dashboard zu tun hat

set -u

YES=0
REVERT_HOSTNAME=0
KEEP_REPO=0

for arg in "$@"; do
    case "$arg" in
        --yes)             YES=1 ;;
        --revert-hostname) REVERT_HOSTNAME=1 ;;
        --keep-repo)       KEEP_REPO=1 ;;
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
echo "═══════════════════════════════════════════════"
echo "  Dashboard-Installation auf dem VPS entfernen"
echo "═══════════════════════════════════════════════"
echo ""
echo "Host:     $(hostname)"
echo "Betrifft: dashboard-agent, Nutzer 'tunnel', ${REPO_DIR}"
[ "$REVERT_HOSTNAME" -eq 1 ] && echo "          zusaetzlich: Hostname zurueck auf 'server'"
[ "$KEEP_REPO" -eq 1 ]       && echo "          Repository bleibt erhalten"
echo ""

if [ "$YES" -eq 0 ]; then
    printf 'Fortfahren? Tippe ENTFERNEN: '
    read -r answer
    [ "$answer" = "ENTFERNEN" ] || { echo "Abgebrochen."; exit 0; }
fi
echo ""

# ── 1. Agent-Dienst ──────────────────────────────────────────
echo "1. systemd-Dienst"

if systemctl list-unit-files 2>/dev/null | grep -q '^dashboard-agent.service'; then
    systemctl disable --now dashboard-agent >/dev/null 2>&1 \
        && ok "Dienst dashboard-agent gestoppt und deaktiviert" \
        || bad "Dienst dashboard-agent liess sich nicht stoppen"
else
    skip "Dienst dashboard-agent"
fi

if [ -f /etc/systemd/system/dashboard-agent.service ]; then
    rm -f /etc/systemd/system/dashboard-agent.service \
        && ok "/etc/systemd/system/dashboard-agent.service" \
        || bad "Unit-Datei liess sich nicht loeschen"
    systemctl daemon-reload
    systemctl reset-failed dashboard-agent >/dev/null 2>&1 || true
else
    skip "Unit-Datei dashboard-agent.service"
fi

# ── 2. Programmdateien ───────────────────────────────────────
echo ""
echo "2. Programmdateien"

if [ -d /opt/dashboard-agent ]; then
    rm -rf /opt/dashboard-agent && ok "/opt/dashboard-agent/" || bad "/opt/dashboard-agent/"
else
    skip "/opt/dashboard-agent/"
fi

# ── 3. Nutzer ────────────────────────────────────────────────
echo ""
echo "3. Nutzer"

# Sicherheitsnetz: niemals den aufrufenden Nutzer oder root anfassen.
remove_user() {
    local u="$1"
    if [ "$u" = "$TARGET_USER" ] || [ "$u" = "root" ]; then
        bad "Nutzer $u wird aus Sicherheitsgruenden nicht entfernt"
        return
    fi
    if ! getent passwd "$u" >/dev/null; then
        skip "Nutzer $u"
        return
    fi
    pkill -KILL -u "$u" 2>/dev/null || true
    sleep 1
    if userdel -r "$u" 2>/dev/null; then
        ok "Nutzer $u samt Home"
    elif userdel "$u" 2>/dev/null; then
        ok "Nutzer $u (Home war nicht vorhanden)"
    else
        bad "Nutzer $u liess sich nicht entfernen"
    fi
}

remove_user dashboard-agent
remove_user tunnel

# Reste, falls userdel das Home stehen liess
for d in /home/tunnel /home/dashboard-agent; do
    [ -d "$d" ] && { rm -rf "$d" && ok "Verzeichnis $d"; }
done

# ── 4. Repository ────────────────────────────────────────────
echo ""
echo "4. Repository"

if [ "$KEEP_REPO" -eq 1 ]; then
    skip "Repository (--keep-repo)"
elif [ -d "$REPO_DIR" ]; then
    rm -rf "$REPO_DIR" && ok "$REPO_DIR" || bad "$REPO_DIR"
else
    skip "$REPO_DIR"
fi

# ── 5. Hostname (optional) ───────────────────────────────────
echo ""
echo "5. Hostname"

if [ "$REVERT_HOSTNAME" -eq 1 ]; then
    hostnamectl set-hostname server && ok "Hostname auf 'server' gesetzt" \
        || bad "Hostname liess sich nicht setzen"
    if grep -q '^127.0.1.1' /etc/hosts; then
        sed -i 's/^127\.0\.1\.1.*/127.0.1.1 server/' /etc/hosts && ok "/etc/hosts angepasst"
    fi
    if grep -q '^preserve_hostname: true' /etc/cloud/cloud.cfg 2>/dev/null; then
        sed -i 's/^preserve_hostname: true/preserve_hostname: false/' /etc/cloud/cloud.cfg \
            && ok "preserve_hostname zurueckgesetzt"
    fi
    update-initramfs -u >/dev/null 2>&1 && ok "initramfs neu gebaut" \
        || bad "initramfs-Neubau fehlgeschlagen"
else
    skip "Hostname (ohne --revert-hostname unveraendert)"
fi

# ── 6. Kontrolle ─────────────────────────────────────────────
echo ""
echo "6. Kontrolle"

if ss -tlpn 2>/dev/null | grep -q ':9100'; then
    bad "Es lauscht weiterhin etwas auf Port 9100"
else
    ok "Port 9100 ist frei"
fi

for u in dashboard-agent tunnel; do
    getent passwd "$u" >/dev/null && bad "Nutzer $u existiert noch"
done

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
echo "Von Hand pruefen, falls gewuenscht:"
echo "  - ~/.ssh/authorized_keys deines eigenen Nutzers (Tunnel-Key war dort nicht)"
echo "  - ausstehende Systemupdates:  apt list --upgradable"
echo ""
