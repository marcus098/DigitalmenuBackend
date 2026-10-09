# Pagamenti online — configurazione

Ogni locale incassa sul **proprio** account di pagamento, scegliendo il provider in
**Dashboard → Impostazioni pagamenti**:

- **Stripe** — con le chiavi API del *suo* account Stripe;
- **SumUp** — con la chiave API del *suo* account SumUp.

I soldi vanno **direttamente** dal cliente al locale: la piattaforma non riceve, non trasferisce e non trattiene
nulla (niente Stripe Connect, niente commissioni di piattaforma). Le chiavi segrete vengono salvate **cifrate**
(AES-256-GCM) nel database e non vengono mai restituite dall'API (si vedono solo le ultime 4 cifre).

---

## Parte 1 — Per il ristoratore

### Stripe

1. Accedi a [dashboard.stripe.com](https://dashboard.stripe.com) con l'account **del locale** e completa
   l'attivazione (dati aziendali, IBAN per i bonifici).
2. *Impostazioni → Metodi di pagamento*: abilita carte, Apple Pay, Google Pay (vengono mostrati automaticamente
   i metodi abilitati qui).
3. Crea le chiavi in *Sviluppatori → Chiavi API*. Due possibilità:
   - **Consigliato — chiave limitata** (*Crea chiave limitata*), con questi permessi:

     | Risorsa | Permesso | Perché |
     |---|---|---|
     | PaymentIntents | Scrittura | creare, incassare e annullare i pagamenti |
     | Refunds (Rimborsi) | Scrittura | rimborsi dalla Cassa e degli ordini rifiutati |
     | Webhook Endpoints | Scrittura | creazione automatica del webhook |
     | Balance (Saldo) | Lettura | verifica della chiave al salvataggio |
     | Account (Connect → Accounts) | Lettura — *facoltativo* | mostra nome/id dell'account nelle impostazioni |

   - oppure la **chiave segreta** standard (`sk_live_…` / `sk_test_…`): funziona, ma dà accesso completo all'account.
4. In **Dashboard → Impostazioni pagamenti → Stripe** incolla:
   - la **chiave pubblicabile** (`pk_live_…` o `pk_test_…`);
   - la **chiave segreta o limitata** (`sk_…` / `rk_…`). Le due chiavi devono essere della stessa modalità (entrambe live o entrambe test).
5. Al salvataggio la chiave viene verificata su Stripe e il **webhook viene creato automaticamente** sul tuo account
   (stato *Webhook: automatico*). Non devi fare altro.
6. **Se il webhook non può essere creato** (stato *Webhook: mancante*, con il motivo — es. chiave limitata senza il
   permesso *Webhook Endpoints*): in Stripe vai in *Sviluppatori → Webhook → Aggiungi endpoint*,
   - URL: quello mostrato nelle impostazioni (`https://api.…/api/payments/webhook/stripe/<token>`);
   - eventi: `payment_intent.succeeded`, `payment_intent.payment_failed`,
     `payment_intent.amount_capturable_updated`, `payment_intent.canceled`, `charge.refunded`;
   - copia il **Signing secret** (`whsec_…`) e incollalo nel campo *Webhook secret*, poi salva
     (stato *Webhook: manuale*).

   Senza webhook Stripe non si può attivare: i pagamenti non verrebbero mai confermati.
7. Seleziona **Stripe** come provider attivo. Ora puoi attivare anche il *pagamento anticipato obbligatorio*
   per asporto e/o tavolo.

Ordini asporto "su richiesta" (riserva dello slot) con prepagamento: l'importo viene solo **autorizzato**; viene
addebitato quando accetti l'ordine, e l'autorizzazione è annullata (nessun addebito) se lo rifiuti o non rispondi in tempo.

Rimborsi: dalla **Cassa**. I pagamenti fatti con un account Stripe diverso da quello attualmente collegato
(es. dopo un cambio account) si rimborsano dalla dashboard Stripe di quell'account.

### SumUp

1. Accedi a [me.sumup.com](https://me.sumup.com) con l'account **del locale**.
2. Crea una chiave API: *Profilo → Impostazioni sviluppatore / Chiavi API* (developer settings) → **Crea chiave**
   (`sup_sk_…`). Copiala subito: viene mostrata una sola volta.
3. In **Dashboard → Impostazioni pagamenti → SumUp** incolla la chiave. Il **codice esercente** (merchant code)
   viene letto automaticamente dal profilo; puoi inserirlo a mano se serve un codice diverso.
4. Seleziona **SumUp** come provider attivo.

Il cliente paga sulla pagina di pagamento SumUp e poi torna al menu; non serve configurare webhook (l'indirizzo di
notifica viene passato da noi a ogni pagamento e lo stato viene sempre riletto da SumUp).

⚠️ **SumUp non supporta la pre-autorizzazione.** Per gli ordini asporto "su richiesta" con prepagamento il
cliente viene **addebitato subito**; se rifiuti l'ordine (o non rispondi entro la scadenza) l'importo viene
**rimborsato automaticamente** per intero. Se un rimborso automatico fallisce, l'errore viene registrato nei log
del server e il rimborso va fatto a mano dalla dashboard SumUp.

### Cambiare o rimuovere il provider

- Puoi cambiare provider attivo in qualunque momento: i pagamenti già aperti restano legati al provider con cui
  sono stati creati (incasso, rimborso e notifiche continuano a funzionare finché le credenziali restano salvate).
- *Disattiva* (provider NONE) o *Rimuovi credenziali* del provider attivo disattiva anche il pagamento anticipato.
- Rimuovere le credenziali Stripe elimina anche il webhook creato automaticamente.

---

## Parte 2 — Per l'amministratore della piattaforma

### Chiave di cifratura (obbligatoria)

Le credenziali dei locali sono cifrate con una chiave AES-256 fornita al backend:

| Variabile d'ambiente | Proprietà Spring |
|---|---|
| `APP_SECRETS_ENCRYPTION_KEY` | `app.secrets.encryption-key` |

```bash
openssl rand -base64 32
```

- Senza chiave il backend **parte comunque** (errore nel log `SEGRETI: ...`), ma i locali non possono salvare
  credenziali (HTTP 503 *"Cifratura non configurata sul server"*) e i pagamenti online risultano disattivati.
- **Fai un backup sicuro della chiave** (password manager / vault), separato dai backup del database.
  Se la chiave va persa, le credenziali salvate non sono più leggibili: **tutti i locali dovranno reinserire le chiavi**.
- **Non ruotarla** senza prima ri-cifrare i dati: con una chiave diversa i valori esistenti non si decifrano
  (vengono trattati come assenti e i pagamenti del locale si disattivano).

### URL pubblici

| Proprietà | Uso |
|---|---|
| `app.public-base-url` | base degli URL dei webhook (`/api/payments/webhook/stripe/{token}`, `/api/payments/webhook/sumup/{token}`). Deve essere HTTPS e raggiungibile da Internet: con `localhost` il webhook Stripe automatico non si può creare. |
| `app.frontend-base-url` | ritorno del cliente dopo il checkout SumUp: `{base}/{localname}/payment/{comandId}?provider=sumup` |

Il `{token}` è un valore casuale per locale (non l'id del locale). Una richiesta con token sconosciuto riceve 400
(Stripe) o 200 ignorato (SumUp).

### API (riferimento)

Admin (ruolo ADMIN, risposta `{data: …}`):

- `GET /api/payments/provider` — impostazioni (mai segreti)
- `PUT /api/payments/provider/stripe` `{publishableKey, secretKey?, webhookSecret?}`
- `PUT /api/payments/provider/sumup` `{apiKey?, merchantCode?}`
- `PUT /api/payments/provider/active` `{provider: NONE|STRIPE|SUMUP}`
- `DELETE /api/payments/provider/stripe`, `DELETE /api/payments/provider/sumup`
- `GET/PUT /api/payments/settings` (prepagamento), `GET /api/payments`, `GET /api/payments/today-total`,
  `POST /api/payments/{id}/refund`

Pubbliche: `GET /api/public/payments/config/{localname}`, `POST /api/public/payments/intent/{localname}`,
`POST /api/public/payments/sumup/{localname}/sync/{checkoutId}`.

Webhook: `POST /api/payments/webhook/stripe/{token}` (firma `Stripe-Signature` verificata con il secret del locale),
`POST /api/payments/webhook/sumup/{token}` (non firmato: il checkout viene riletto da SumUp con la chiave del locale).

### Test in locale (Stripe)

Il webhook automatico non si può creare su `localhost`: usa la Stripe CLI con l'account di test del locale e incolla
il secret stampato nel campo *Webhook secret*.

```bash
stripe login
stripe listen --forward-to localhost:8080/api/payments/webhook/stripe/<token-del-locale>
```

Carte di test: `4242 4242 4242 4242` (ok), `4000 0025 0000 3155` (3DS), `4000 0000 0000 9995` (rifiutata).

### Checklist go-live

- [ ] `APP_SECRETS_ENCRYPTION_KEY` impostata e salvata in un posto sicuro
- [ ] `app.public-base-url` = dominio pubblico HTTPS del backend, `app.frontend-base-url` = dominio del frontend
- [ ] Ogni locale ha inserito le chiavi **live** del proprio account e vede *Webhook: automatico/manuale* (Stripe)
- [ ] Prova end-to-end con una carta reale e rimborso dalla Cassa
