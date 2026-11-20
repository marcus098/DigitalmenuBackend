# Print bridge su ESP32

Fa lo stesso lavoro di `print-bridge/bridge.js` senza tenere un PC acceso: l'ESP32 si collega al Wi-Fi del
locale, ogni 2,5 secondi chiede al server se ci sono comande e le manda alla stampante di rete sulla porta 9100.
Consuma meno di 1 W e riparte da solo dopo un blackout.

Serve una stampante ESC/POS **di rete** (LAN o Wi-Fi) con IP fisso. Il Wi-Fi deve essere a **2,4 GHz**.

## 1. Stampante in dashboard

Dashboard → **Stampanti** → *Aggiungi* → tipo **Bridge ESC/POS**. Copia il **token**: viene mostrato una sola
volta (se lo perdi usa *Rigenera token*). Se il locale usava RawBT sul tablet per la stessa stampante, quella
voce può restare o essere disattivata.

## 2. Arduino IDE

1. Installa Arduino IDE 2 da https://www.arduino.cc/en/software.
2. *File → Preferenze → URL aggiuntive per il Gestore schede*:
   `https://espressif.github.io/arduino-esp32/package_esp32_index.json`
3. *Strumenti → Scheda → Gestore schede*: cerca **esp32** (Espressif Systems) e installa la versione 3.x.

Non servono altre librerie.

## 3. Configurazione

Nella cartella `print-bridge-esp32/print-bridge-esp32/` copia `config.example.h` in `config.h` e compila Wi-Fi,
token e IP della stampante. `config.h` è escluso da git.

## 4. Caricamento

1. Collega l'ESP32 al PC con un cavo USB-C **dati** (alcuni cavi caricano soltanto).
   Se la scheda ha due porte, usa quella marcata **COM** o **UART**.
2. Apri `print-bridge-esp32.ino`, scegli la scheda (*ESP32 Dev Module*, oppure *ESP32S3 Dev Module* /
   *ESP32C3 Dev Module* secondo il chip scritto sul modulo) e la porta COM.
3. *Carica*. Se resta fermo su "Connecting....", tieni premuto **BOOT**, premi e rilascia **RST/EN**, poi
   rilascia BOOT.
4. *Strumenti → Monitor seriale* a **115200**: deve comparire `Wi-Fi ok, IP ...`.
   Su ESP32-C3/C6 collegati con la USB nativa, se il monitor resta vuoto imposta
   *Strumenti → USB CDC On Boot → Enabled* e ricarica.

Se non sai quale chip hai, scegli una scheda qualsiasi e premi *Carica*: se è sbagliata l'errore dice il chip
reale (es. `This chip is ESP32-C3, not ESP32`).

Premendo **BOOT** a scheda accesa esce lo scontrino `PROVA STAMPA` (prova locale, non passa dal server).
Poi dalla dashboard *Stampa di prova*: entro pochi secondi esce lo scontrino TEST.

Dopo il caricamento basta alimentarlo con un qualsiasi alimentatore USB da telefono vicino alla stampante.

## Problemi

| Monitor seriale | Causa |
|---|---|
| `Wi-Fi non raggiungibile` | nome/password sbagliati o rete solo 5 GHz |
| `token non valido ... (404)` | token sbagliato/rigenerato o tipo stampante diverso da "Bridge ESC/POS" |
| `stampante non raggiungibile` | IP sbagliato, stampante spenta o su un'altra rete |
| `server: HTTP -1` | niente internet o server irraggiungibile |

La lista **Ultimi job** della stampante in dashboard mostra stato ed errori dei singoli job.
