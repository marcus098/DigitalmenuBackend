# Deploy sul server — guida per Claude Code

Questa guida è scritta per essere eseguita da **Claude Code sul server** (Ubuntu + Docker + Caddy).
Prompt suggerito da dare a Claude sul server:

> Leggi `~/DigitalmenuBackend/docs/SERVER_DEPLOY.md` ed esegui il deploy passo per passo.
> Fermati e chiedimi prima di ogni passo marcato 🧑 e prima di qualsiasi operazione distruttiva.

---

## Regole per Claude

1. **Mai stampare segreti** (`cat .env`, `cat config/*/application.properties`, `docker compose config`, `env`).
   Per controllare che una chiave sia valorizzata usa: `grep -c '^APP_SECRETS_ENCRYPTION_KEY=.\+' .env`.
2. **Mai committare** `.env`, `config/`, `application.properties`. Sul server non si fa `git commit`/`push`.
3. Prima di `docker compose down -v`, `docker volume rm`, `DROP`, `rm -rf` → **chiedi**. I volumi contengono i dati di produzione.
4. Se un passo fallisce, diagnostica con i log (`docker compose logs --tail=200 <servizio>`) prima di cambiare configurazione.
5. Se esiste già un deploy funzionante, fai il **backup** (passo 2) prima di tutto.

---

## Architettura (cosa gira)

| Servizio | Porta (solo 127.0.0.1) | Dominio via Caddy |
|---|---|---|
| frontend (nginx + React) | 3000 | `app.DOMINIO` |
| backend (`main-app`, Spring servlet) | 8085 | `api.DOMINIO` |
| webflux (SSE dashboard realtime) | 8081 | `reactive.DOMINIO` |
| postgres / mongodb / kafka | interne alla rete docker | — |
| postgres-backup | cron → bucket B2 | — |

Repo, entrambi clonati nella home dell'utente di deploy, **affiancati** (il compose usa `../DigitalmenuBackend`):

```
~/DigitalmenuFrontend   ← docker-compose.yml, .env, config/, deploy/Caddyfile
~/DigitalmenuBackend    ← Dockerfile, Dockerfile.webflux, deploy/config/*.example, docs/
```

Branch da deployare: `master` in entrambi i repo.

---

## 1. Verifica prerequisiti

```bash
docker --version && docker compose version
caddy version
free -h        # servono almeno 4 GB RAM (2 JVM + Postgres + Mongo + Kafka)
df -h ~        # almeno 10 GB liberi per le build
ls ~/DigitalmenuFrontend ~/DigitalmenuBackend 2>/dev/null
```

Se Docker/Caddy mancano: `~/DigitalmenuFrontend/deploy/bootstrap-ubuntu.sh` (idempotente; richiede sudo → 🧑 chiedi).

## 2. Backup (solo se c'è già un deploy attivo)

```bash
cd ~/DigitalmenuFrontend
mkdir -p ~/backups/$(date +%F)
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' | gzip > ~/backups/$(date +%F)/postgres.sql.gz
docker compose exec -T mongodb mongodump --archive --gzip > ~/backups/$(date +%F)/mongo.archive.gz
ls -lh ~/backups/$(date +%F)
cp .env ~/backups/$(date +%F)/env.bak && chmod 600 ~/backups/$(date +%F)/env.bak
```

Verifica che i file non siano vuoti prima di proseguire.

## 3. Codice

```bash
cd ~ 
[ -d DigitalmenuBackend ]  || git clone https://github.com/marcus098/DigitalmenuBackend.git
[ -d DigitalmenuFrontend ] || git clone https://github.com/marcus098/DigitalmenuFrontend.git

cd ~/DigitalmenuBackend  && git fetch origin && git checkout master && git pull --ff-only
cd ~/DigitalmenuFrontend && git fetch origin && git checkout master && git pull --ff-only
git -C ~/DigitalmenuBackend log -1 --oneline; git -C ~/DigitalmenuFrontend log -1 --oneline
```

Se `git status` mostra modifiche locali sul server, **non** scartarle: mostrale e chiedi 🧑.

## 4. `.env` del compose

```bash
cd ~/DigitalmenuFrontend
[ -f .env ] || cp .env.compose.example .env
chmod 600 .env
```

Controlla quali chiavi mancano o sono ancora segnaposto (senza stampare i valori):

```bash
grep -nE '=(|.*REPLACE_ME.*)$' .env | cut -d= -f1
```

🧑 I valori li inserisce l'utente (`nano .env`). Chiavi obbligatorie:

| Variabile | Note |
|---|---|
| `FRONTEND_URL`, `BACKEND_URL`, `WEBFLUX_URL` | `https://app.DOMINIO`, `https://api.DOMINIO`, `https://reactive.DOMINIO` |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | se c'è già un DB, **devono restare quelli vecchi** |
| `BUCKET_*` | Backblaze B2 — **chiavi nuove** (le vecchie vanno ruotate) |
| `APP_SECRETS_ENCRYPTION_KEY` | chiave AES-256 che cifra le chiavi di pagamento dei locali (passata al backend dal compose). Generala senza mostrarla (vedi sotto). **Backup obbligatorio**: se si perde, tutti i locali devono reinserire le chiavi. Non cambiarla su un deploy esistente |
| `BACKUP_S3_BUCKET` | bucket B2 separato per i backup |

Generazione di `APP_SECRETS_ENCRYPTION_KEY` (solo se non c'è già un valore: su un deploy esistente **non** va cambiata):

```bash
grep -q '^APP_SECRETS_ENCRYPTION_KEY=.\+' .env || {
  K=$(openssl rand -base64 32)
  if grep -q '^APP_SECRETS_ENCRYPTION_KEY=' .env; then sed -i "s|^APP_SECRETS_ENCRYPTION_KEY=.*|APP_SECRETS_ENCRYPTION_KEY=$K|" .env
  else echo "APP_SECRETS_ENCRYPTION_KEY=$K" >> .env; fi
  unset K; }
```

🧑 Ricorda all'utente di copiare la chiave in un password manager (`grep '^APP_SECRETS_ENCRYPTION_KEY=' .env` lo
lancia **lui**, non Claude). Senza la chiave il backend parte ma i pagamenti online restano disattivati.

Le variabili `STRIPE_*` delle versioni precedenti (Stripe Connect) non servono più: se presenti in `.env` si possono
rimuovere.

Le `REACT_APP_*` vengono "cotte" nel bundle in fase di build: se cambi un URL o la `pk_` devi rifare la build del frontend.

## 5. Config Spring (`config/`)

Le immagini Docker **non** contengono `application.properties`; il compose monta `./config/main-app` e `./config/webflux`.

```bash
cd ~/DigitalmenuFrontend
mkdir -p config/main-app config/webflux
[ -f config/main-app/application.properties ] || cp ../DigitalmenuBackend/deploy/config/main-app.application.properties.example config/main-app/application.properties
[ -f config/webflux/application.properties ]  || cp ../DigitalmenuBackend/deploy/config/webflux.application.properties.example  config/webflux/application.properties
chmod 700 config && chmod 600 config/*/application.properties
grep -n 'REPLACE_ME\|example.com' config/*/application.properties | cut -d= -f1
```

Da fare:
- Sostituisci `example.com` col dominio reale (Claude può farlo con `sed -i 's/example\.com/DOMINIO/g'` dopo che l'utente gli ha detto il dominio).
- Genera una chiave JWT e mettila **identica** in entrambi i file (Claude può farlo senza mostrarla):
  ```bash
  K=$(openssl rand -base64 64 | tr -d '\n')
  sed -i "s|^jwt.application.key=.*|jwt.application.key=$K|" config/main-app/application.properties config/webflux/application.properties
  unset K
  ```
  ⚠️ Se c'è già un deploy, cambiare la chiave fa sloggare tutti: chiedi 🧑 se preferisce riusare quella vecchia.
- 🧑 L'utente inserisce credenziali SMTP (password per app Gmail **nuova**) e le credenziali Postgres in `config/webflux` (stesse di `.env`).
- I valori di datasource/mongo/kafka/bucket e `app.secrets.encryption-key` nei file sono sovrascritti dalle variabili del compose: non serve duplicarli.

## 6. Caddy

```bash
sudo cp ~/DigitalmenuFrontend/deploy/Caddyfile /etc/caddy/Caddyfile   # 🧑 solo se non è già personalizzato: fai prima diff
sudo sed -i 's/example\.com/DOMINIO/g' /etc/caddy/Caddyfile            # e l'email per Let's Encrypt
sudo caddy validate --config /etc/caddy/Caddyfile && sudo systemctl reload caddy
```

I record DNS `app.`, `api.`, `reactive.` devono già puntare al server (`dig +short api.DOMINIO`).

## 7. Build e avvio

```bash
cd ~/DigitalmenuFrontend
docker compose build            # 5-10 minuti la prima volta (Maven)
docker compose up -d
docker compose ps
docker compose logs --tail=100 backend webflux
```

Atteso nei log del backend: `Started MainAppApplication`. Errori tipici:

| Errore | Causa |
|---|---|
| `SEGRETI: app.secrets.encryption-key ... non impostata` (ERROR, il backend parte comunque) | manca `APP_SECRETS_ENCRYPTION_KEY` in `.env` o il compose non la passa al backend: i pagamenti online restano disattivati |
| `Could not resolve placeholder 'jwt.application.key'` / `bucket.*.name` | `config/main-app/application.properties` non montato o incompleto |
| `password authentication failed` | credenziali Postgres diverse da quelle con cui è stato creato il volume |
| `Connection to kafka:9092 could not be established` | Kafka ancora in avvio: attendi 30s e `docker compose restart backend webflux` |
| pull di `bitnami/kafka` fallisce | Bitnami ha ritirato le immagini gratuite: usa `bitnamilegacy/kafka:latest` oppure `apache/kafka` (env diverse) — 🧑 chiedi |

Lo schema DB viene aggiornato da Hibernate (`ddl-auto=update`): nuove tabelle per pagamenti, slot, ecc. vengono create al primo avvio.

## 8. Pagamenti online (ogni locale configura il proprio account)

Non c'è nessuna configurazione Stripe/SumUp a livello di piattaforma: ogni ristorante incassa sul **proprio**
account e inserisce le sue chiavi in **Dashboard → Impostazioni pagamenti** (Stripe o SumUp). Le chiavi sono salvate
cifrate con `APP_SECRETS_ENCRYPTION_KEY`; il webhook Stripe viene creato automaticamente sull'account del locale.

Guida completa da girare ai ristoratori: `docs/PAGAMENTI_SETUP.md`.

Requisiti lato server (già coperti dai passi precedenti):
1. `APP_SECRETS_ENCRYPTION_KEY` impostata (passo 4) e salvata in un posto sicuro.
2. `app.public-base-url=https://api.DOMINIO` e `app.frontend-base-url=https://app.DOMINIO` (passo 5): servono per
   gli URL dei webhook e per il ritorno del cliente da SumUp.
3. Nei log del backend **non** deve comparire l'errore `SEGRETI: ...`.

## 8bis. Superadmin (pannello della piattaforma)

Il superadmin (noi) vede tutti i locali, gestisce i dati di abbonamento e le note interne e può entrare come un
locale ("accesso come supporto", token di 60 minuti, tutto registrato in `superadmin_audit`). Non esiste una
registrazione: l'utente viene creato dal backend all'avvio.

Solo la **prima volta**:

1. Genera una password robusta (≥ 12 caratteri) e aggiungi le variabili a `.env` (🧑 la password la sceglie/salva
   l'utente nel password manager; Claude non la stampa):
   ```bash
   cd ~/DigitalmenuFrontend
   grep -q '^APP_SUPERADMIN_EMAIL=' .env || echo 'APP_SUPERADMIN_EMAIL=' >> .env
   grep -q '^APP_SUPERADMIN_PASSWORD=' .env || echo 'APP_SUPERADMIN_PASSWORD=' >> .env
   # 🧑 l'utente inserisce l'email e una password, es. generata con:  openssl rand -base64 24
   ```
2. Riavvia il backend: `docker compose up -d backend` e controlla nei log
   `docker compose logs backend | grep SUPERADMIN` → atteso `SUPERADMIN: creato utente <id> (<email>)`.
   Errori possibili: `password troppo corta` (minimo 12), `esiste già un utente ... con email` (email già usata da
   un locale: scegline un'altra).
3. 🧑 Login dalla dashboard con email e password, poi **rimuovi la password da `.env`**
   (`sed -i 's|^APP_SUPERADMIN_PASSWORD=.*|APP_SUPERADMIN_PASSWORD=|' .env`) e riavvia il backend.

Il bootstrap non sovrascrive mai la password di un utente esistente: rimettere la variabile non la cambia.
Il nome locale `superadmin` è riservato (collide con la rotta `/superadmin` del frontend).

## 9. Verifica

```bash
curl -sI https://app.DOMINIO | head -1                                   # 200
curl -s -o /dev/null -w '%{http_code}\n' https://api.DOMINIO/api/printers/tablet/status   # 401/403 = backend vivo e protetto
curl -s -o /dev/null -w '%{http_code}\n' -X POST -d '{}' https://api.DOMINIO/api/payments/webhook/stripe/token-a-caso   # 400 = raggiungibile, token sconosciuto
curl -s -o /dev/null -w '%{http_code}\n' -X POST -H 'Content-Type: application/json' -d '{}' https://api.DOMINIO/api/payments/webhook/sumup/token-a-caso   # 200 = raggiungibile (token sconosciuto ignorato)
curl -s -N --max-time 5 -o /dev/null -w '%{http_code}\n' https://reactive.DOMINIO/api/auth/admin   # 401 senza token
```

Poi 🧑 test manuale dall'utente:
1. Login dashboard, creare un tavolo, aprire il QR dal telefono, fare un ordine → deve comparire in tempo reale in dashboard.
2. Impostazioni pagamenti: inserire le chiavi Stripe **di test** di un locale (`pk_test_`/`sk_test_`) → deve
   comparire *Webhook: automatico*; attivare Stripe e pagare un ordine con la carta `4242 4242 4242 4242`.
3. Stampanti → nuova stampante "Tablet Android (RawBT)" → Stampa di prova.

## 10. Aggiornamenti successivi

```bash
cd ~/DigitalmenuBackend  && git pull --ff-only
cd ~/DigitalmenuFrontend && git pull --ff-only
docker compose build && docker compose up -d
docker image prune -f
```

Rollback: `git checkout <commit precedente>` in entrambi i repo, `docker compose build && docker compose up -d`.
Ripristino DB dal backup del passo 2: 🧑 sempre su richiesta esplicita.

## Cose note ancora aperte

- Nessuna migrazione versionata (si usa `ddl-auto=update`): non rinominare colonne a mano.
- Un pagamento che arriva per un ordine già annullato viene solo loggato (rimborso manuale dalla dashboard del provider).
- Le colonne `agencies.stripe_*` / `application_fee_bps` dell'era Stripe Connect restano nel DB ma non sono più usate.
- I pagamenti creati con Stripe Connect (prima del passaggio agli account dei locali) si rimborsano dalla dashboard
  Stripe dell'account Connect.
