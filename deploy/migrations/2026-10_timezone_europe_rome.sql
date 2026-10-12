-- ─────────────────────────────────────────────────────────────────────────────
-- Migrazione una tantum: JVM del backend da UTC a Europe/Rome (ottobre 2026)
--
-- Le colonne Postgres "timestamp without time zone" mappate su LocalDateTime contengono l'ora
-- "da orologio a muro" della JVM che le ha scritte. Fino al passaggio a Europe/Rome i container
-- giravano in UTC, quindi le righe vecchie sono in ora UTC; quelle nuove saranno in ora italiana.
-- Questo script converte le righe vecchie in ora italiana (DST incluso), così totali "di oggi",
-- storico pagamenti e pausa asporto restano coerenti.
--
-- Colonne interessate (le altre date in Postgres sono OffsetDateTime/Instant = istanti, non cambiano;
-- date/ore delle prenotazioni e chiusure sono LocalDate/LocalTime inserite dall'utente, non cambiano;
-- Mongo salva istanti, non serve migrarlo):
--   payments.created_at, payments.updated_at
--   takeaway_slot_config.paused_until
--
-- QUANDO: con backend FERMO, dopo aver fermato la versione vecchia e prima di avviare quella nuova
--   (altrimenti le righe scritte dalla versione nuova verrebbero spostate di nuovo):
--     docker compose stop backend webflux
--     docker compose exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" "$POSTGRES_DB"' \
--         < ../DigitalmenuBackend/deploy/migrations/2026-10_timezone_europe_rome.sql
--     docker compose build && docker compose up -d
-- IDEMPOTENTE: registra l'esecuzione in tz_migration_log; una seconda esecuzione non fa nulla.
-- Le colonne già "with time zone" (o assenti) vengono saltate.
-- Fare prima il backup (docs/SERVER_DEPLOY.md §backup).
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS tz_migration_log (
    name       varchar(100) PRIMARY KEY,
    applied_at timestamptz  NOT NULL DEFAULT now()
);

DO $$
DECLARE
    col record;
BEGIN
    IF EXISTS (SELECT 1 FROM tz_migration_log WHERE name = '2026-10_utc_to_europe_rome') THEN
        RAISE NOTICE 'Migrazione fuso orario già applicata: niente da fare';
        RETURN;
    END IF;

    FOR col IN
        SELECT c.table_name, c.column_name
        FROM information_schema.columns c
        WHERE c.table_schema = current_schema()
          AND c.data_type = 'timestamp without time zone'
          AND (c.table_name, c.column_name) IN (('payments', 'created_at'),
                                                ('payments', 'updated_at'),
                                                ('takeaway_slot_config', 'paused_until'))
    LOOP
        -- ora UTC "a muro" → istante → ora italiana "a muro"
        EXECUTE format('UPDATE %I SET %I = (%I AT TIME ZONE ''UTC'') AT TIME ZONE ''Europe/Rome'' WHERE %I IS NOT NULL',
                       col.table_name, col.column_name, col.column_name, col.column_name);
        RAISE NOTICE 'Convertita %.%', col.table_name, col.column_name;
    END LOOP;

    INSERT INTO tz_migration_log(name) VALUES ('2026-10_utc_to_europe_rome');
END $$;
