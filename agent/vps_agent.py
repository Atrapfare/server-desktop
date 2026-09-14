#!/usr/bin/env python3
"""Liefert Eckdaten des Systems als JSON unter GET /stats.

Bindet ausschliesslich an localhost. Der Zugriff vom Heimserver laeuft
ueber einen SSH-Tunnel, damit kein zusaetzlicher Port nach aussen offen
steht. Bewusst ohne Fremdbibliotheken, damit auf dem VPS nichts weiter
installiert und gepflegt werden muss.
"""

import json
import os
import socket
import sys
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HOST = "127.0.0.1"
PORT = 9100


def uptime_seconds():
    with open("/proc/uptime", "r", encoding="ascii") as handle:
        return int(float(handle.read().split()[0]))


def load_average():
    with open("/proc/loadavg", "r", encoding="ascii") as handle:
        fields = handle.read().split()
    return [float(value) for value in fields[:3]]


def memory():
    values = {}
    with open("/proc/meminfo", "r", encoding="ascii") as handle:
        for line in handle:
            key, _, rest = line.partition(":")
            values[key] = int(rest.split()[0])  # kB

    total_kb = values.get("MemTotal", 0)
    # MemAvailable beruecksichtigt zurueckgewinnbaren Cache und beschreibt
    # damit besser, was wirklich belegt ist, als MemFree.
    available_kb = values.get("MemAvailable", values.get("MemFree", 0))
    return {
        "totalMb": total_kb // 1024,
        "usedMb": (total_kb - available_kb) // 1024,
    }


def disk(path="/"):
    stat = os.statvfs(path)
    total = stat.f_blocks * stat.f_frsize
    # Wie df: belegt ist alles ausser den freien Bloecken, inklusive der fuer
    # root reservierten.
    used = (stat.f_blocks - stat.f_bfree) * stat.f_frsize
    gib = 1024 ** 3
    return {
        "totalGb": total // gib,
        "usedGb": used // gib,
    }


def collect():
    return {
        "hostname": socket.gethostname(),
        "uptimeSeconds": uptime_seconds(),
        "load": load_average(),
        "cpuCount": os.cpu_count() or 1,
        "memory": memory(),
        "disk": disk(),
        "timestamp": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    }


class StatsHandler(BaseHTTPRequestHandler):

    server_version = "vps-agent/1.0"

    def do_GET(self):
        if self.path.split("?")[0] != "/stats":
            self.send_error(404, "not found")
            return

        try:
            body = json.dumps(collect()).encode("utf-8")
        except OSError as error:
            self.send_error(500, "collect failed: %s" % error)
            return

        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        # journald bekaeme sonst fuer jeden Abruf eine Zeile - bei 30 s
        # Intervall sind das 2880 Zeilen am Tag ohne Erkenntniswert.
        pass


def main():
    server = ThreadingHTTPServer((HOST, PORT), StatsHandler)
    print("vps-agent hoert auf http://%s:%d/stats" % (HOST, PORT), file=sys.stderr)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
