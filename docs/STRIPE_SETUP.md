# Pagamenti online con Stripe Connect

Ogni locale incassa sul **proprio** account Stripe (Connect **Express**). La piattaforma crea i pagamenti come
**destination charge** con `on_behalf_of` + `transfer_data.destination`:

- il locale è il *settlement merchant*: il suo nome compare sull'estratto conto del cliente, i fondi arrivano sul suo saldo
  e i bonifici (payout) partono verso il suo IBAN;
- la piattaforma può trattenere una commissione (`application_fee_amount`) configurabile per locale;
- le chiavi API usate dal frontend sono quelle della **piattaforma** (nessuna opzione `stripeAccount` in `loadStripe`).

## 1. Account piattaforma

1. Crea (o usa) l'account Stripe della società che gestisce il SaaS e completa l'attivazione (dati societari, IBAN).
2. **Connect → Inizia**: scegli il modello *piattaforma / marketplace*, account **Express**.
3. *Connect → Impostazioni*:
   - **Branding**: nome piattaforma, icona, colore (sono mostrati nell'onboarding Express e nella dashboard Express dei locali);
   - **Paesi**: abilita almeno Italia;
   - **Express dashboard**: lascia attiva la gestione dei payout da parte del locale;
   - verifica che le capability `card_payments` e `transfers` siano richiedibili per l'Italia.
4. *Impostazioni → Metodi di pagamento*: abilita carte, Apple Pay, Google Pay (il PaymentIntent usa
   `automatic_payment_methods`, quindi i metodi mostrati dipendono da questa configurazione).
   Per Apple Pay registra il dominio del frontend (*Impostazioni → Metodi di pagamento → Domini*).

## 2. Variabili d'ambiente (backend main-app)

| Variabile | Proprietà Spring | Obbligatoria | Descrizione |
|---|---|---|---|
| `STRIPE_SECRET_KEY` | `stripe.secret-key` | sì | Secret key piattaforma (`sk_test_…` / `sk_live_…`). Se vuota i pagamenti rispondono 503. |
| `STRIPE_WEBHOOK_SECRET` | `stripe.webhook-secret` | sì (avvio) | Signing secret dell'endpoint webhook **piattaforma** (`whsec_…`). |
| `STRIPE_CONNECT_WEBHOOK_SECRET` | `stripe.connect-webhook-secret` | consigliata | Signing secret dell'endpoint webhook **Connect** (`whsec_…`). Se vuota l'endpoint risponde 400 e lo stato del locale si aggiorna solo aprendo la pagina "Pagamenti online". |
| `STRIPE_CONNECT_DEFAULT_FEE_BPS` | `stripe.connect.default-fee-bps` | no (default `0`) | Commissione piattaforma di default in basis points (100 = 1%). Override per locale nella colonna `agencies.application_fee_bps`. |
| `APP_FRONTEND_BASE_URL` | `app.frontend-base-url` | no | URL pubblico del frontend, usato per i link di ritorno dell'onboarding (`{base}/{localname}/Dashboard/PaymentsSettings?stripe=return|refresh`). Default: `app.frontend.url`, poi `http://localhost:3000`. |

Frontend: `REACT_APP_STRIPE_PUBLISHABLE_KEY` = publishable key **della piattaforma** (`pk_test_…` / `pk_live_…`).

Commissione per singolo locale (finché non esiste una UI di super-admin):

```sql
UPDATE agencies SET application_fee_bps = 150 WHERE id = 42; -- 1,5%
```

## 3. Endpoint webhook

Crea **due** endpoint in *Sviluppatori → Webhook*:

### a) Endpoint piattaforma — `https://<api>/api/payments/webhook`
"Eventi del tuo account". Eventi:

- `payment_intent.succeeded` → pagamento `COMPLETED`, comanda marcata `paid`, notifica realtime alle dashboard
- `payment_intent.payment_failed` → `FAILED` (l'intent resta riutilizzabile con un altro metodo)
- `payment_intent.amount_capturable_updated` → `AUTHORIZED` (intent con `capture_method=manual`, usato per gli
  ordini asporto "su richiesta" con prepagamento): la comanda passa da `AWAIT_PAYMENT` ad `AWAIT_APPROVAL`.
  Se il locale accetta l'importo viene incassato (capture, poi arriva `payment_intent.succeeded`); se rifiuta o non
  risponde entro la scadenza l'autorizzazione viene annullata (nessun addebito, nessuna commissione di rimborso)
- `payment_intent.canceled` → `CANCELED`
- `charge.refunded` → `REFUNDED` / `PARTIALLY_REFUNDED` con `refundedCents` (rimborso totale: comanda `paid=false`, `refunded=true`)

**Prepagamento** (Impostazioni pagamenti → "Pagamento anticipato obbligatorio" per asporto e/o tavolo, attivabile
solo con `chargesEnabled`): la comanda nasce in `AWAIT_PAYMENT`, non viene stampata né mostrata alle dashboard
finché il pagamento non è confermato; dopo 15 minuti senza pagamento viene annullata e lo slot liberato.

Il signing secret va in `STRIPE_WEBHOOK_SECRET`.

### b) Endpoint Connect — `https://<api>/api/payments/webhook/connect`
"Eventi degli account collegati". Eventi:

- `account.updated` → aggiorna `stripe_charges_enabled` / `stripe_details_submitted` del locale

Il signing secret va in `STRIPE_CONNECT_WEBHOOK_SECRET`.

Entrambi gli endpoint sono pubblici (autenticati dalla firma `Stripe-Signature`) e idempotenti: i retry di Stripe
e gli eventi fuori ordine non duplicano effetti né fanno regredire gli stati.

## 4. Flusso

1. L'admin del locale apre **Dashboard → Altro → Pagamenti online** e clicca **Collega Stripe**
   (`POST /api/payments/connect/onboard`): viene creato l'account Express (paese IT) e si viene rediretti all'onboarding Stripe.
2. Al ritorno (`?stripe=return`) la pagina chiama `GET /api/payments/connect/status`, che rilegge l'account da Stripe.
   Quando `chargesEnabled = true` i clienti vedono **Paga online** (`GET /api/public/payments/config/{localname}`).
3. Il cliente paga dalla pagina ordine: `POST /api/public/payments/intent/{localname}` (importo calcolato dal server),
   conferma con Stripe Elements (eventuale 3DS con redirect a `/{localname}/payment/{comandId}?payment_intent=…&redirect_status=…`).
4. Il webhook marca la comanda pagata; dashboard e cassa si aggiornano in tempo reale (badge **Pagato**).
5. Rimborsi dalla **Cassa** (`POST /api/payments/{id}/refund`): `reverse_transfer` + `refund_application_fee`,
   quindi i fondi vengono ripresi dal saldo del locale e la commissione restituita.
6. Se una comanda viene eliminata, gli intent ancora aperti vengono annullati su Stripe.

## 5. Test in locale

```bash
stripe login
stripe listen \
  --forward-to localhost:8080/api/payments/webhook \
  --forward-connect-to localhost:8080/api/payments/webhook/connect
```

`stripe listen` stampa **un solo** `whsec_…` valido per entrambi gli inoltri: usalo sia per `STRIPE_WEBHOOK_SECRET`
sia per `STRIPE_CONNECT_WEBHOOK_SECRET`.

Onboarding in test mode: usa dati fittizi, telefono `000 000 0000`, codice SMS `000-000`, IBAN `IT40S0542811101000000123456`.

Carte di test (qualsiasi data futura, qualsiasi CVC):

| Carta | Esito |
|---|---|
| `4242 4242 4242 4242` | pagamento riuscito |
| `4000 0025 0000 3155` | richiede autenticazione 3DS |
| `4000 0000 0000 9995` | rifiutata (fondi insufficienti) |
| `4000 0000 0000 0077` | riuscito, fondi disponibili subito sul saldo (utile per testare i rimborsi) |

Eventi simulati: `stripe trigger payment_intent.succeeded`, `stripe trigger charge.refunded`
(gli eventi generati da `trigger` non sono legati a comande reali: vengono ignorati, ma verificano firma e raggiungibilità).

## 6. Checklist go-live

- [ ] Account piattaforma attivato in live mode, Connect Express approvato per l'Italia
- [ ] Branding Connect completato (nome, logo, colori, URL assistenza)
- [ ] `STRIPE_SECRET_KEY=sk_live_…` e `REACT_APP_STRIPE_PUBLISHABLE_KEY=pk_live_…` (stessa modalità!)
- [ ] Endpoint webhook **live** creati (piattaforma + Connect) con gli eventi sopra; secret live in env
- [ ] `APP_FRONTEND_BASE_URL` = dominio pubblico HTTPS del frontend
- [ ] Dominio registrato per Apple Pay in live mode
- [ ] Commissione di default (`STRIPE_CONNECT_DEFAULT_FEE_BPS`) concordata e comunicata ai locali (contratto/T&C)
- [ ] Ogni locale ha rifatto l'onboarding in **live** (gli account di test non esistono in live)
- [ ] Prova end-to-end con una carta reale e rimborso dalla Cassa
- [ ] Monitoraggio: alert su errori dei webhook (Dashboard Stripe → Webhook → consegne fallite)
