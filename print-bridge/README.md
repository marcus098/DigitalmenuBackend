# Print bridge ESC/POS

Piccolo programma Node.js che gira su un PC già presente nel locale (es. il PC cassa) e fa da ponte tra il
server e una stampante termica ESC/POS economica in rete locale (Rongta RP332, Xprinter XP-Q200, Munbyn, ...)
oppure Bluetooth abbinata al PC Windows (es. Netum NT-1809DD, vedi sotto).

Le stampanti Star CloudPRNT (mC-Print2/3, TSP143IV) **non** hanno bisogno del bridge: si collegano direttamente
al server tramite l'URL CloudPRNT mostrato in dashboard.

## Come funziona

1. Ogni 2-3 secondi il bridge chiede al server `GET /api/printers/bridge/{deviceToken}/next`.
2. Se c'è una comanda da stampare riceve i byte ESC/POS già pronti (grassetto, testo grande, taglio carta).
3. Li invia alla stampante via TCP sulla porta 9100.
4. Conferma al server (`POST .../jobs/{jobId}/ack`): se la stampa fallisce il server ritenta fino a 5 volte.

Non apre porte in ingresso: funziona dietro qualsiasi router/NAT.

## Requisiti

- Windows 10/11 (o Linux/macOS) con **Node.js 18 o superiore** (https://nodejs.org, versione LTS).
- Stampante ESC/POS **di rete (LAN/Wi-Fi)** con IP fisso (impostalo dal router o dal pannello della stampante).
  Verifica: stampa l'autotest della stampante (tenendo premuto FEED all'accensione) per leggere l'IP.

### Stampanti Bluetooth (es. Netum NT-1809DD) — solo Windows

Serve quando nel locale si usano iPhone/iPad (che non possono stampare in Bluetooth dal browser) oppure per
avere la stampa automatica senza toccare il tablet. Il PC con il bridge deve stare **acceso e a portata
Bluetooth** della stampante (circa 5–10 m, meglio senza muri in mezzo).

1. Accendi la stampante. Su Windows: **Impostazioni → Bluetooth e dispositivi → Aggiungi dispositivo →
   Bluetooth**, scegli la stampante (PIN di solito `0000` o `1234`).
2. Trova la porta COM: **Pannello di controllo → Hardware e suoni → Dispositivi e stampanti** →
   tasto destro sulla stampante → *Proprietà* → scheda **Hardware** (oppure **Servizi**): c'è una voce tipo
   "Collegamento seriale standard su Bluetooth (**COM5**)".
   In alternativa: *Impostazioni → Bluetooth e dispositivi → Dispositivi → Altre impostazioni Bluetooth →
   scheda Porte COM*: usa quella in **Uscita** (*Outgoing*) col nome della stampante.
   Se compaiono due porte, quella giusta è quella in *Uscita*.
3. Copia `config.bluetooth.example.json` in `config.json` e metti la porta trovata:
   ```json
   {
     "serverUrl": "https://api.tuodominio.it",
     "deviceToken": "IL-TOKEN-COPIATO",
     "printer": { "type": "serial", "path": "COM5" },
     "pollIntervalMs": 2500
   }
   ```
4. **Prova locale** (non serve il token, non passa dal server):
   ```
   node bridge.js --test
   ```
   Deve uscire lo scontrino "PROVA STAMPA". La prima volta può metterci qualche secondo (connessione Bluetooth).
5. In dashboard crea la stampante di tipo **Bridge ESC/POS** (non "Tablet RawBT") e avvia `node bridge.js`
   come descritto sotto. Da quel momento le comande escono da sole, qualunque dispositivo le invii (anche iPhone).

Note:
- La porta viene aperta solo durante la stampa: tra una comanda e l'altra la stampante resta libera.
- Se l'app del produttore (es. Thermer) è connessa alla stampante dal telefono, il PC non riesce a collegarsi:
  scollega il telefono.
- Stampanti **seriali vere o USB-seriale**: stesso config, aggiungi `"baudRate": 9600` (o quanto indicato nel
  manuale). Le porte Bluetooth ignorano questo valore.
- Su Linux/macOS la porta COM non è supportata: usa una stampante di rete.

### Stampanti USB

Il bridge parla TCP/9100 o porta COM. Con una stampante USB "pura" (senza porta COM) hai due strade:
- **consigliato**: usare un modello LAN (stessa fascia di prezzo, 50-100 €);
- condividere la stampante USB da Windows e usare un software che esponga una porta RAW 9100
  (es. driver del produttore con "porta di rete virtuale"): soluzione più fragile, sconsigliata.

## Installazione

1. In dashboard → **Stampanti** → *Aggiungi* → tipo **Bridge ESC/POS**. Copia il **token** (viene mostrato una
   sola volta; se lo perdi usa *Rigenera token*).
2. Copia la cartella `print-bridge` sul PC, ad esempio in `C:\print-bridge`.
3. Copia `config.example.json` in `config.json` e compila:
   ```json
   {
     "serverUrl": "https://api.tuodominio.it",
     "deviceToken": "IL-TOKEN-COPIATO",
     "printer": { "host": "192.168.1.50", "port": 9100 },
     "pollIntervalMs": 2500
   }
   ```
4. Prova da terminale:
   ```
   cd C:\print-bridge
   node bridge.js
   ```
   Dalla dashboard premi **Stampa di prova**: entro pochi secondi deve uscire lo scontrino TEST.

Per più stampanti (es. cucina + bar) crea una stampante per ciascuna in dashboard e avvia un bridge per
ciascuna con il suo config: `node bridge.js C:\print-bridge\config-bar.json`.

## Avvio automatico su Windows

### Opzione A — servizio Windows con NSSM (consigliata)

Parte all'accensione del PC anche senza login e si riavvia da solo se si chiude.

1. Scarica NSSM da https://nssm.cc/download ed estrai `nssm.exe` (cartella `win64`) in `C:\print-bridge`.
2. Apri il **Prompt dei comandi come amministratore**:
   ```
   cd C:\print-bridge
   nssm install PrintBridge "C:\Program Files\nodejs\node.exe" "C:\print-bridge\bridge.js" "C:\print-bridge\config.json"
   nssm set PrintBridge AppDirectory C:\print-bridge
   nssm set PrintBridge AppStdout C:\print-bridge\bridge.log
   nssm set PrintBridge AppStderr C:\print-bridge\bridge.log
   nssm set PrintBridge AppRotateFiles 1
   nssm set PrintBridge AppRotateBytes 1048576
   nssm start PrintBridge
   ```
3. Controllo: `nssm status PrintBridge`, log in `C:\print-bridge\bridge.log`.
   Per rimuoverlo: `nssm stop PrintBridge` e `nssm remove PrintBridge confirm`.

### Opzione B — Utilità di pianificazione (all'accesso)

1. Apri **Utilità di pianificazione** → *Crea attività*.
2. Generale: nome `PrintBridge`, *Esegui solo se l'utente è connesso* (o *indipendentemente* se hai la password).
3. Attivazione: **All'accesso** dell'utente della cassa.
4. Azione: *Avvia programma* → `"C:\Program Files\nodejs\node.exe"`,
   argomenti `C:\print-bridge\bridge.js C:\print-bridge\config.json`, *Inizia in* `C:\print-bridge`.
5. Impostazioni: togli *Arresta l'attività se è in esecuzione da più di 3 giorni*; spunta
   *Se l'attività non riesce, riavvia ogni 1 minuto*.

## Risoluzione problemi

| Messaggio | Causa |
|---|---|
| `token non valido o stampante eliminata (404)` | token rigenerato/sbagliato oppure tipo stampante non "Bridge ESC/POS" |
| `connect ECONNREFUSED` / `timeout stampante` | IP o porta errati, stampante spenta o su un'altra rete |
| `COM5 non esiste` | numero porta sbagliato: ricontrolla le porte COM (passo 2 Bluetooth) |
| `timeout apertura COM5` | stampante spenta, fuori portata, non abbinata o collegata a un altro dispositivo |
| `COM5 occupata o non accessibile` | un altro programma usa la porta (app del produttore, altro bridge) |
| lettere accentate come `a'` | voluto: il testo è ASCII puro per essere compatibile con tutte le stampanti |
| la carta non viene tagliata | il modello non ha la taglierina: strappare a mano |

Nella dashboard, la lista **Ultimi job** di ogni stampante mostra lo stato (PENDING, SENT, PRINTED, FAILED) e
l'eventuale errore.
