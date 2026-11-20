/*
 * Print bridge ESC/POS su ESP32: stesso protocollo di print-bridge/bridge.js, senza PC acceso.
 *
 *   ogni POLL_INTERVAL_MS  GET  {SERVER_URL}/api/printers/bridge/{token}/next  → 200 {jobId, kind, comandId, escposBase64} | 204
 *   byte ESC/POS          → TCP {PRINTER_HOST}:{PRINTER_PORT}
 *   esito                 POST {SERVER_URL}/api/printers/bridge/{token}/jobs/{jobId}/ack  {ok, error}
 *
 * Premere il tasto BOOT a bridge acceso stampa uno scontrino di prova locale (non passa dal server).
 * Board: qualsiasi ESP32 / ESP32-S3 / C3 con core Arduino-ESP32 3.x, nessuna libreria esterna.
 */
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <mbedtls/base64.h>
#include <vector>
#include "config.h"

// BOOT_PIN (tasto BOOT) è definito dal core per ogni chip: 0 su ESP32/S3, 9 su C3/C6
static const uint32_t WIFI_RESTART_MS = 5UL * 60 * 1000;   // senza Wi-Fi da 5 minuti → riavvio
static const uint32_t TOKEN_ERROR_BACKOFF_MS = 60UL * 1000;

static WiFiClientSecure tls;
static uint32_t wifiLostSince = 0;

// ---------- Wi-Fi ----------

static void connectWifi() {
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.printf("Wi-Fi: connessione a \"%s\"", WIFI_SSID);
  for (int i = 0; i < 60 && WiFi.status() != WL_CONNECTED; i++) {
    delay(500);
    Serial.print('.');
  }
  Serial.println();
  if (WiFi.status() == WL_CONNECTED) {
    Serial.printf("Wi-Fi ok, IP %s, segnale %d dBm\n", WiFi.localIP().toString().c_str(), WiFi.RSSI());
  } else {
    Serial.println("Wi-Fi non raggiungibile: controlla nome/password (solo reti 2,4 GHz)");
  }
}

static bool wifiReady() {
  if (WiFi.status() == WL_CONNECTED) {
    wifiLostSince = 0;
    return true;
  }
  if (wifiLostSince == 0) wifiLostSince = millis();
  if (millis() - wifiLostSince > WIFI_RESTART_MS) {
    Serial.println("Wi-Fi assente da troppo tempo: riavvio");
    ESP.restart();
  }
  WiFi.reconnect();
  return false;
}

// ---------- Stampante ----------

// Invia i byte alla stampante di rete. Restituisce "" se ok, altrimenti il messaggio d'errore.
static String sendToPrinter(const uint8_t *data, size_t len) {
  WiFiClient printer;
  printer.setTimeout(5);
  if (!printer.connect(PRINTER_HOST, PRINTER_PORT, 5000)) {
    return String("stampante non raggiungibile su ") + PRINTER_HOST + ":" + PRINTER_PORT;
  }
  size_t sent = 0;
  while (sent < len) {
    size_t n = printer.write(data + sent, min((size_t)1024, len - sent));
    if (n == 0) {
      printer.stop();
      return "invio interrotto dalla stampante";
    }
    sent += n;
  }
  printer.flush();
  delay(200);   // lascia alla stampante il tempo di leggere prima di chiudere
  printer.stop();
  return "";
}

// Comandi ESC/POS come byte (contengono 0x00, quindi niente stringhe C)
static void put(std::vector<uint8_t> &b, std::initializer_list<uint8_t> bytes) { b.insert(b.end(), bytes); }
static void put(std::vector<uint8_t> &b, const String &s) { b.insert(b.end(), s.begin(), s.end()); }

static void printLocalTest() {
  std::vector<uint8_t> b;
  put(b, {0x1B, '@'});                       // init
  put(b, {0x1B, 'a', 1, 0x1D, '!', 0x11});   // centrato, doppia dimensione
  put(b, String("PROVA STAMPA\n"));
  put(b, {0x1D, '!', 0});
  put(b, String("ESP32 print bridge\n"));
  put(b, "IP " + WiFi.localIP().toString() + "\n");
  put(b, {0x1B, 'a', 0, 0x1B, 'd', 4});      // sinistra, avanza 4 righe
  put(b, {0x1D, 'V', 66, 0});                // taglio parziale
  String err = sendToPrinter(b.data(), b.size());
  Serial.println(err.isEmpty() ? "Prova stampa inviata" : "Prova stampa fallita: " + err);
}

// ---------- Server ----------

// Estrae il valore stringa di "key" da un JSON piatto (i campi del BridgeJob non contengono escape).
static String jsonField(const String &json, const char *key) {
  String needle = String("\"") + key + "\":\"";
  int start = json.indexOf(needle);
  if (start < 0) return "";
  start += needle.length();
  int end = json.indexOf('"', start);
  return end < 0 ? "" : json.substring(start, end);
}

static String endpoint(const String &path) {
  return String(SERVER_URL) + "/api/printers/bridge/" + DEVICE_TOKEN + path;
}

static void ack(const String &jobId, const String &error) {
  HTTPClient http;
  if (!http.begin(tls, endpoint("/jobs/" + jobId + "/ack"))) return;
  http.addHeader("Content-Type", "application/json");
  String safe = error;
  safe.replace("\\", "/");
  safe.replace("\"", "'");
  String body = error.isEmpty() ? String("{\"ok\":true}") : "{\"ok\":false,\"error\":\"" + safe + "\"}";
  int code = http.POST(body);
  http.end();
  if (code != 200) Serial.printf("ack %s: HTTP %d\n", jobId.c_str(), code);
}

// Una richiesta /next. Restituisce l'attesa prima della prossima (ms).
static uint32_t pollOnce() {
  HTTPClient http;
  http.setTimeout(15000);
  if (!http.begin(tls, endpoint("/next"))) return POLL_INTERVAL_MS;
  int code = http.GET();

  if (code == 204) { http.end(); return POLL_INTERVAL_MS; }
  if (code == 404) {
    http.end();
    Serial.println("token non valido o stampante eliminata (404): controlla DEVICE_TOKEN e il tipo \"Bridge ESC/POS\"");
    return TOKEN_ERROR_BACKOFF_MS;
  }
  if (code != 200) {
    http.end();
    Serial.printf("server: HTTP %d\n", code);
    return POLL_INTERVAL_MS * 2;
  }

  String json = http.getString();
  http.end();
  String jobId = jsonField(json, "jobId");
  String b64 = jsonField(json, "escposBase64");
  if (jobId.isEmpty() || b64.isEmpty()) {
    Serial.println("risposta /next non valida");
    return POLL_INTERVAL_MS;
  }

  size_t len = 0;
  mbedtls_base64_decode(nullptr, 0, &len, (const unsigned char *)b64.c_str(), b64.length());
  uint8_t *bytes = (uint8_t *)malloc(len);
  if (!bytes) {
    ack(jobId, "memoria insufficiente sull'ESP32");
    return POLL_INTERVAL_MS;
  }
  String err;
  if (mbedtls_base64_decode(bytes, len, &len, (const unsigned char *)b64.c_str(), b64.length()) != 0) {
    err = "base64 non valido";
  } else {
    err = sendToPrinter(bytes, len);
  }
  free(bytes);

  Serial.printf("job %s (%s, comanda %s): %s\n", jobId.c_str(), jsonField(json, "kind").c_str(),
                jsonField(json, "comandId").c_str(), err.isEmpty() ? "stampato" : err.c_str());
  ack(jobId, err);
  return err.isEmpty() ? 200 : POLL_INTERVAL_MS;   // se c'era un job, controlla subito se ce ne sono altri
}

// ---------- Avvio ----------

void setup() {
  Serial.begin(115200);
  delay(300);
  Serial.println("\nPrint bridge ESP32");
  pinMode(BOOT_PIN, INPUT_PULLUP);

  // Il token nel path è l'unica credenziale: HTTPS sempre, verifica certificato disattivata per non
  // dover aggiornare il firmware quando il certificato del server viene rinnovato.
  tls.setInsecure();

  connectWifi();
}

// Attende ms controllando nel frattempo il tasto BOOT (stampa di prova).
static void waitWatchingButton(uint32_t ms) {
  uint32_t start = millis();
  while (millis() - start < ms) {
    if (digitalRead(BOOT_PIN) == LOW) {
      printLocalTest();
      while (digitalRead(BOOT_PIN) == LOW) delay(20);
    }
    delay(20);
  }
}

void loop() {
  if (!wifiReady()) {
    delay(2000);
    return;
  }
  waitWatchingButton(pollOnce());
}
