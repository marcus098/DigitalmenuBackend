# Consegna a domicilio

Il cliente sceglie "Consegna a domicilio" dal menu del locale (come l'asporto), indica la posizione,
verifica il telefono con un codice SMS (solo se la verifica è attiva sul server, vedi sotto) e paga online o alla
consegna, secondo le regole del locale.

## Impostazioni del locale (`delivery_settings`, una riga per agency)

| Campo | Note |
|---|---|
| `enabled` | Il canale compare nel menu solo se attivo |
| `originLat`, `originLng`, `originAddress` | Punto di partenza (geocodificato dall'indirizzo del locale, il titolare può spostare il pin) |
| `zones` | JSON `[{ "maxKm": 3, "feeCents": 200, "minOrderCents": 1500 }, …]` ordinate per `maxKm`; oltre l'ultima = fuori zona |
| `freeDeliveryOverCents` | Opzionale: consegna gratis sopra questo subtotale |
| `paymentMode` | `ONLINE_REQUIRED` · `CUSTOMER_CHOICE` · `ON_DELIVERY_ONLY` |
| `onDeliveryMethods` | `["CASH","CARD"]` (contanti / POS del rider) |
| `slotMinutes`, `deliveriesPerSlot`, `leadMinutes` | Fasce orarie con capienza propria (riusa la logica degli slot asporto, config separata); `leadMinutes` = preavviso minimo |
| `weeklyHours` | Come gli slot asporto (giorni/orari); chiusure e pausa riusano quelle dell'asporto o equivalenti dedicate |
| `requirePhoneOtpTakeaway` | Il locale può chiedere la verifica SMS anche per l'asporto (default false) |

Distanza = linea d'aria (haversine) dal punto di partenza. Calcolata **solo sul server**; il client mostra una stima.

## Verifica del telefono (OTP via Prelude Verify)

Stesso fornitore di azzara-be (`PreludeVerificationService`: `POST https://api.prelude.dev/v2/verification` e
`/v2/verification/check`, `Authorization: Bearer <PRELUDE_API_KEY>`), chiave solo lato server
(`prelude.api-key=${PRELUDE_API_KEY:}`).

- `POST /api/public/otp/send` `{ "localname", "phone" }` → 200 `{ "sent": true }`.
  Telefono normalizzato in E.164 (default +39). Limiti anti "SMS pumping": per IP, per telefono, per locale al giorno
  (vedi `OtpRateLimitService` in azzara-be) → 429 `{ "message" }`.
- `POST /api/public/otp/verify` `{ "localname", "phone", "code" }` → 200 `{ "phoneToken", "expiresAt" }`,
  401 `{ "message": "Codice non valido" }`.
- `phoneToken`: token firmato dal server (telefono E.164 + scadenza **90 giorni**). Il client lo salva in localStorage
  per numero: chi ha già verificato non riceve un nuovo SMS (meno costi).
- **Sviluppo locale**: se `PRELUDE_API_KEY` è vuota e `app.otp.dev-mode=true`, nessun SMS: il codice valido è `123456`
  (loggato). In produzione dev-mode deve essere false.

### Verifica facoltativa (`OtpService.enabled()`)

La verifica del telefono è **attiva solo se gli SMS sono disponibili**: `app.otp.dev-mode` effettiva **oppure**
`prelude.api-key` impostata, e `app.otp.enabled` (env `APP_OTP_ENABLED`, predefinito `true`) non è `false`.
`app.otp.enabled=false` spegne la verifica anche con la chiave impostata.

Con la verifica **non attiva** (es. produzione senza `PRELUDE_API_KEY`):
- `POST /api/public/orders/delivery/{localname}` non richiede `phoneToken`: il telefono è solo validato e normalizzato
  in E.164 (numero non valido → 400) e salvato come sempre.
- Asporto: `requirePhoneOtpTakeaway` è ignorato (nessun 401).
- `GET /api/public/delivery/{localname}` risponde `phoneVerification: false` e `requirePhoneOtpTakeaway: false`;
  il menu mostra solo nome e telefono, senza codice. Anche `GET /api/delivery/settings` (titolare) espone
  `phoneVerification` (sola lettura): la dashboard disattiva l'opzione SMS per l'asporto.
- `/api/public/otp/send` e `/verify` rispondono 503 (nessun SMS inviato).

## Geocodifica

- `GET /api/public/geo/search?localname=&q=` → max 5 risultati `{ label, lat, lng }` vicini al locale.
- `GET /api/public/geo/reverse?localname=&lat=&lng=` → `{ label, street, houseNumber, city, postcode }`.
- Proxy lato server verso Nominatim (OpenStreetMap) con User-Agent dedicato, cache e limite 1 richiesta/s
  (policy Nominatim: **niente autocompletamento a ogni tasto**: ricerca con pulsante/invio). In produzione si potrà
  passare a un fornitore a pagamento cambiando solo il proxy.
- Mappa lato client: Leaflet + tile OpenStreetMap (con attribuzione), pin trascinabile.

## Ordine

`POST /api/public/orders/delivery/{localname}`

```json
{
  "customerName": "Giulia Bianchi",
  "customerPhone": "+393331234567",
  "phoneToken": "…",
  "address": { "label": "Via Milano 12, Brescia", "lat": 45.54, "lng": 10.21,
               "intercom": "Bianchi", "floor": "2", "notes": "cancello verde" },
  "deliveryTime": "2026-11-20T20:15",
  "paymentChoice": "ONLINE" | "CASH" | "CARD",
  "orders": [ /* come l'asporto, con variants */ ]
}
```

Risposta come l'asporto: `{ id, status, paymentRequired, subtotalCents, deliveryFeeCents, totalCents }`.
Errori 400 `{ "message" }` in italiano: fuori zona, sotto l'ordine minimo della zona, fascia piena/chiusa, telefono non
verificato o token scaduto (401), metodo di pagamento non ammesso dal locale.

- `PrepaymentPolicy.Channel.DELIVERY`: `ONLINE` → comanda in `AWAIT_PAYMENT` come l'asporto prepagato.
- Comanda: `orderType: "DELIVERY"` (o equivalente al campo tipo esistente) + snapshot `delivery`:
  `{ address…, distanceKm, feeCents, paymentChoice, stage }` dove `stage` ∈ `PREPARING` · `OUT_FOR_DELIVERY` · `DELIVERED`
  (campo separato: lo stato della comanda resta quello esistente, nessun nuovo valore in `ComandStatus`).
- La tariffa di consegna entra in totale, pagamenti, cassa e report (riga "Consegna").
- Comanda stampata: blocco **DOMICILIO** con indirizzo, citofono, piano, note, telefono, orario e
  `PAGATO ONLINE` oppure `DA INCASSARE € 23,50 CONTANTI/POS`.

## Rider / consegna

- Ruolo `ROLE_DELIVERY` esistente: invito come i camerieri. Anche titolare e camerieri possono consegnare.
- `GET /api/delivery/orders` (consegne del giorno), `POST /api/delivery/orders/{id}/stage` `{ "stage" }`
  (`OUT_FOR_DELIVERY` / `DELIVERED`, con `cashCollected` se pagamento alla consegna).
- Ogni cambio di stage pubblica l'evento d'ordine (SSE) → pagina stato del cliente aggiornata.
- Vista rider (mobile): lista consegne per orario, link "Naviga" (Google Maps / Apple Maps), "Chiama", "Consegnato".

## Fuori da questa versione
Tracciamento GPS del rider, SMS di notifica "in consegna" (costo per SMS), zone a poligono, integrazione con
piattaforme di consegna esterne.

## Implementazione backend (note e precisazioni al contratto)

Migrazione: `deploy/migrations/2026-11_home_delivery.sql` (idempotente): tabella `delivery_settings`. Le comande restano
su Mongo (campo `delivery`), le fasce usano la collezione Mongo `delivery_slot_counters`.

### Endpoint

| Metodo | Path | Chi | Note |
|---|---|---|---|
| GET | `/api/public/delivery/{localname}` | pubblico | impostazioni per il menu (vedi sotto) |
| GET | `/api/public/delivery/{localname}/slots/{yyyy-MM-dd}` | pubblico | solo fasce `AVAILABLE` (stesso `SlotDto` dell'asporto: `time`, `status`, `orderCount`, `maxOrders`) |
| POST | `/api/public/orders/delivery/{localname}` | pubblico | come da contratto |
| POST | `/api/public/otp/send`, `/api/public/otp/verify` | pubblico | come da contratto |
| GET | `/api/public/geo/search?localname=&q=`, `/api/public/geo/reverse?localname=&lat=&lng=` | pubblico | come da contratto |
| GET/PUT | `/api/delivery/settings` | ROLE_ADMIN | corpo = `DeliverySettings` (deliveryTypes.ts) + `weeklyHours` |
| GET | `/api/delivery/slots/day/{yyyy-MM-dd}` | ADMIN, WAITER, DELIVERY | tutte le fasce del giorno con occupazione (`AVAILABLE`/`FULL`/`PAST`/`CLOSED`) |
| GET | `/api/delivery/orders?date=yyyy-MM-dd` | ADMIN, WAITER, DELIVERY | consegne del giorno (default oggi) |
| POST | `/api/delivery/orders/{id}/stage` | ADMIN, WAITER, DELIVERY | `{ "stage", "cashCollected"? }` |

Tutti gli errori sono `{ "message": "..." }` in italiano.

### Impostazioni

- `GET/PUT /api/delivery/settings`: stessi campi di `DeliverySettings` più `weeklyHours`
  (`{ "MONDAY": [{"start":"19:00","end":"23:00"}], ... }`, **`null` = stessi orari degli slot asporto**) e
  `onlinePaymentAvailable` (sola lettura). Se `originAddress` non è ancora impostato, il GET propone l'indirizzo del
  locale (stile) senza coordinate: la dashboard lo geocodifica con `/api/public/geo/search`.
- Validazione PUT (400): zone 1..10, `maxKm` in (0, 50] e distinti (il server le ordina), `feeCents` 0..10000,
  `minOrderCents` 0..100000; con `enabled: true` servono origine e almeno una zona; `slotMinutes` 5..120,
  `deliveriesPerSlot` 1..50, `leadMinutes` 0..1440; per `CUSTOMER_CHOICE`/`ON_DELIVERY_ONLY` almeno un metodo
  alla consegna; `ONLINE_REQUIRED` solo con pagamenti online attivi. `freeDeliveryOverCents` ≤ 0 o null = nessuna soglia.
- `GET /api/public/delivery/{localname}` restituisce gli stessi campi (senza `weeklyHours`) più:
  `paymentOptions` (metodi selezionabili ORA, es. `["ONLINE","CASH","CARD"]`), `onlinePaymentAvailable`, `paused`
  ("Sospendi asporto" attivo: niente fasce) e `maxKm` (raggio massimo). Con la consegna disattivata risponde comunque
  (con `enabled: false`), così il menu legge anche `requirePhoneOtpTakeaway`. `phoneVerification` = verifica SMS
  attiva sul server (vedi "Verifica facoltativa").
- **Pagamento**: `ONLINE` è proposto solo se i pagamenti online del locale sono attivi. Con `ONLINE_REQUIRED` e
  pagamenti disattivati il prepagamento decade come per l'asporto (`paymentOptions` = metodi alla consegna, CASH se
  nessuno). `PrepaymentPolicy.Channel.DELIVERY` = `ONLINE_REQUIRED` AND pagamenti attivi.

### Fasce

Stessa logica degli slot asporto con config propria: fasce da `slotMinutes` dentro `weeklyHours` (o gli orari
dell'asporto), capienza `deliveriesPerSlot` **consegne** (nessuna riserva "su richiesta"); una fascia è prenotabile se
inizia almeno `leadMinutes` dopo adesso. **Chiusure** (giorni/periodi/date straordinarie) e **"Sospendi asporto"**
sono quelle dell'asporto e valgono anche per il domicilio. `deliveryTime` deve essere l'inizio di una fascia
(`yyyy-MM-ddTHH:mm`). Contano le comande HOME non eliminate (online + cameriere).

### Ordine

- Validazione in quest'ordine: consegna attiva → dati → telefono (numero non valido 400; con la verifica attiva
  **401** `Telefono non verificato o verifica scaduta: richiedi un nuovo codice`) → metodo di pagamento in `paymentOptions` → coordinate → prodotti/varianti →
  zona (`Indirizzo fuori dalla zona di consegna (8,1 km, consegniamo entro 6,0 km)`) → ordine minimo
  (`Ordine minimo per la tua zona: € 20,00 (mancano € 4,00)`) → fascia. Tutti **400** tranne il telefono (401).
  L'ordine minimo e la soglia `freeDeliveryOverCents` si confrontano con il subtotale (prodotti con varianti ed extra,
  consegna esclusa).
- Limite: 10 ordini a domicilio per IP ogni 10 minuti (`app.delivery.orders-per-ip-per-10min`, in locale 200) → 429.
- Stato iniziale: `ONLINE` → `AWAIT_PAYMENT` (stesso flusso dell'asporto prepagato: pagina di pagamento con importo =
  totale **compresa la consegna**, annullata dopo 15 minuti); `CASH`/`CARD` → `PENDING` (stampa + dashboard subito).
- **Tipo**: nessun campo `orderType` nuovo: la comanda ha `comandWaiterType: "HOME"` (tipo esistente "Domicilio") e
  lo snapshot `delivery` (`ComandDelivery` di deliveryTypes.ts: `label, lat, lng, intercom, floor, notes, distanceKm,
  feeCents, paymentChoice, stage, cashCollected` + `outForDeliveryAt`, `deliveredAt`, `stageBy`). Inoltre `name`,
  `phone` (E.164), `address` (= label) e `time` (= deliveryTime) come per l'asporto.
- **Totali**: il totale della comanda per i pagamenti online include `delivery.feeCents`. La vista pubblica
  `GET /api/public/orders/{id}` (webflux) ha `totalCents` = prodotti + consegna e in più `subtotalCents`,
  `deliveryFeeCents` e `delivery` **redatta** (`stage, paymentChoice, feeCents, distanceKm, cashCollected,
  deliveryTime, outForDeliveryAt, deliveredAt`: niente indirizzo/citofono/note). Dashboard e app staff ricevono la
  comanda completa (con `delivery`): la **cassa** deve aggiungere la riga "Consegna" (`delivery.feeCents`) al
  subtotale che invia a `/checkout` (il backend registra gli importi inviati dalla cassa, come oggi), così incassi e
  report la includono.

### Ordini "Domicilio" del cameriere

`POST /api/orders/insertWaiter` con `comandWaiterType: "HOME"` crea ora anche lo snapshot `delivery`
(stage `PREPARING`, pagamento `CASH` se non indicato) e occupa la fascia di consegna (senza controllo capacità, come
l'asporto del cameriere). Campi facoltativi nuovi: `lat`, `lng`, `intercom`, `floor`, `deliveryNotes`,
`paymentChoice` (`CASH`/`CARD`/`ONLINE`), `deliveryFeeCents` (tariffa decisa dallo staff). Senza coordinate né
`deliveryFeeCents` la tariffa è 0; con le coordinate è quella della zona (nessun controllo di zona/minimo).
Le comande HOME precedenti (senza `delivery`) compaiono nella lista del rider con un `delivery` ricostruito
(indirizzo libero, contanti, `PREPARING`).

### Rider

- `GET /api/delivery/orders` → array ordinato per orario di `{ id, status, createdAt, deliveryTime, customerName,
  phone, paid, subtotalCents, deliveryFeeCents, totalCents, toCollectCents, delivery, orders }`
  (`toCollectCents` = 0 se pagato online). Esclusi `DELETED`, `AWAIT_PAYMENT`, `AWAIT_APPROVAL`.
- `POST /api/delivery/orders/{id}/stage`: solo in avanti (`PREPARING → OUT_FOR_DELIVERY → DELIVERED`, anche
  `PREPARING → DELIVERED`); indietro o ripetuto → 409; stage diverso da `OUT_FOR_DELIVERY`/`DELIVERED` → 400.
  Per `DELIVERED` di un ordine da incassare (`CASH`/`CARD` non pagato) `cashCollected` (boolean) è **obbligatorio**
  (400 se manca; `false` = non incassato, da gestire in cassa). Risposta: il `delivery` aggiornato.
  Comanda annullata o in attesa di pagamento → 409. Lo stato della comanda non cambia.
- Ogni cambio pubblica l'evento d'ordine Kafka/SSE con un campo in più: `{ id, status, idAgency, deliveryStage }`
  (inoltrato da webflux su `/api/public/updates` e `/api/auth/admin`). La pagina stato del cliente, ricevuto un evento
  con il suo `id`, ricarica `GET /api/public/orders/{id}`.
- **Ruolo rider** (`ROLE_DELIVERY`): invito con lo stesso link dei camerieri (`POST /api/signupWaiter` con
  `role: "ROLE_DELIVERY"`; assente = `ROLE_WAITER`, ogni altro ruolo → `false`/400), approvazione del titolare con
  `/api/users/confirmWaiterAdmin/{id}` (prima: login 406), elenco in `/api/users/getWaiters` con il campo `role`,
  eliminazione con `/api/users/deleteWaiter/{id}`. Conferma/eliminazione solo su camerieri e rider del proprio locale
  (altrimenti 404). `controlsVariable` del rider = prodotto con `IS_DELIVERY = 17` (es. 7 × 17 = 119 con email
  confermata). Il rider può usare solo `/api/delivery/orders`, `/api/delivery/orders/{id}/stage`,
  `/api/delivery/slots/day/{date}`, il proprio profilo (`/api/users/updateData`, `changePassword`, `confirmGeneral`)
  e lo stream SSE `/api/auth/admin` (eventi della sua agency); tutto il resto risponde 403, comprese
  `/api/dashboard/getAll` e `/api/comands/**` del webflux.
- Correzione collegata: lo username degli invitati ora è univoco tra gli utenti (prima dal secondo invitato si
  ripeteva, es. `trattoriadamarco1`, e le chiamate autenticate di quell'utente andavano in errore 500).

### Comanda stampata

Blocco DOMICILIO (ESC/POS, StarPRNT, testo CloudPRNT, tablet RawBT, anteprima `text`): `DOMICILIO` e `ORE 20:15` in
grande, cliente, indirizzo (grassetto), `Citofono:`, `Piano:`, `Note consegna:`, `Tel:`, `Distanza: 1,2 km` e in
negativo `PAGATO ONLINE` oppure `DA INCASSARE EUR 28,00 CONTANTI` / `... POS` (importo = prodotti + consegna;
"EUR" perché le stampanti usano solo ASCII).

### Verifica SMS

- Limiti (prima di chiamare Prelude, configurabili `app.otp.rate-limit.*`): invio 5 per IP / 2 h, 3 per numero /
  15 min, 200 per locale / giorno, 2000 globali / giorno; verifica 5 tentativi per numero (10 per IP) / 30 min.
  In locale i limiti per IP e numero sono alzati. 429 `{ message }`.
- `send`: 400 numero non valido, 503 verifica non attiva (nessuna chiave o `app.otp.enabled=false`), 502 errore Prelude. `verify`: 401 `Codice non valido`.
- `phoneToken`: `pt1.<payload>.<HMAC-SHA256>` con telefono E.164 e scadenza 90 giorni; segreto
  `app.otp.token-secret` o, se vuoto, derivato da `jwt.application.key`. Non è legato al locale (prova il numero).
  `expiresAt` è ISO-8601 UTC.
- **Dev-mode** (`app.otp.dev-mode=true`, attiva nel file locale): con `PRELUDE_API_KEY` vuota nessun SMS e codice
  `123456`. Viene **disattivata automaticamente** (ERROR nel log; il backend parte comunque e la verifica risponde
  503) se l'ambiente non è locale: `app.public-base-url` non localhost, `server.forward-headers-strategy` impostato
  (produzione dietro Caddy) o profilo `prod`/`production`/`staging`. Con la chiave impostata si inviano sempre SMS.
- Asporto: se `requirePhoneOtpTakeaway` è attivo (e la verifica è attiva sul server),
  `POST /api/public/orders/takeaway/{localname}` richiede anche `phoneToken` (stesso numero di `customerPhone`),
  altrimenti 401.
- Produzione: aggiungere `PRELUDE_API_KEY` (e `APP_GEO_CONTACT_EMAIL`) all'ambiente del servizio main-app nel
  docker-compose / `.env` (repo frontend) e le proprietà di `deploy/config/main-app.application.properties.example`.
  Il `docker-compose.yml` passa al backend SOLO le variabili elencate in `environment:`: se `PRELUDE_API_KEY` manca
  lì il backend non vede la chiave: la verifica risulta disattivata e gli ordini passano senza codice (log all'avvio
  `OTP: prelude.api-key non impostata: verifica SMS del telefono disattivata`).
- Esiti Prelude (`POST /v2/verification`): `success`/`retry` = inviato; `challenged`/`shadow_blocked` = accettato
  con riserva (WARN: il codice può arrivare su un canale non SMS); `blocked` = **nessun invio** → 422
  (`invalid_phone_line`: numero che non riceve SMS), 400 (`invalid_phone_number`), 429 (`repeated_attempts`).
  Errori HTTP: 401/403 (chiave rifiutata) e 5xx → 503, 429 → 429, altri 4xx (es. credito esaurito) → 502.
  Ogni risposta è loggata con id verifica, status, reason, method, channels e request_id (numero mascherato,
  mai la chiave): `docker compose logs backend | grep Prelude`.
- `prelude.preferred-channel` (`PRELUDE_PREFERRED_CHANNEL`, facoltativo): `sms`, `whatsapp`, `rcs`, ... Vuoto =
  instradamento automatico di Prelude (può scegliere un canale diverso dall'SMS).

### Geocodifica

- `search`: risultati `{ label, lat, lng, street, houseNumber, city, postcode }` (max 5), limitati all'Italia e a un
  riquadro di `maxKm + 2` km (min 5) intorno al punto di partenza, ordinati per distanza; almeno 3 caratteri (400).
  Senza origine configurata: tutta Italia.
- `reverse`: `lat`/`lng` restano quelli del pin; 404 se nessun indirizzo.
- 1 richiesta/s globale verso Nominatim (attesa max 4 s, poi 503), cache 24 h, 30 ricerche per IP ogni 10 minuti
  (429). User-Agent `RestaurantFlow/1.0 (<app.geo.nominatim.contact-email>)`.
