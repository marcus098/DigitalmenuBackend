// Copia questo file in "config.h" (stessa cartella) e compila i valori.
// config.h contiene password e token: non va committato.
#pragma once

// Wi-Fi del locale (solo 2,4 GHz: l'ESP32 non vede le reti a 5 GHz)
#define WIFI_SSID      "NomeReteWiFi"
#define WIFI_PASSWORD  "PasswordWiFi"

// Server e token della stampante "Bridge ESC/POS" creata in dashboard
#define SERVER_URL     "https://menu.axiomgroup.it"
#define DEVICE_TOKEN   "INCOLLA-QUI-IL-TOKEN-DELLA-DASHBOARD"

// Stampante di rete ESC/POS (IP fisso, porta RAW)
#define PRINTER_HOST   "192.168.1.7"
#define PRINTER_PORT   9100

// Ogni quanto chiedere nuove comande al server (ms)
#define POLL_INTERVAL_MS 2500
