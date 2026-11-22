-- ─────────────────────────────────────────────────────────────────────────────
-- Consegna a domicilio (novembre 2026) — vedi docs/DOMICILIO.md
--
-- delivery_settings: impostazioni della consegna, una riga per locale (zone, pagamento, fasce, verifica SMS asporto).
-- Le comande restano su MongoDB (campo "delivery" nella collezione comand, nessuna migrazione) e le fasce usano la
-- collezione Mongo delivery_slot_counters (creata al primo uso).
--
-- Con spring.jpa.hibernate.ddl-auto=update la tabella la crea già Hibernate all'avvio: lo script serve per creare
-- la tabella prima del deploy, con i default lato DB, e per ambienti senza ddl-auto.
--
-- QUANDO: prima o subito dopo il deploy della versione nuova (può girare con il backend acceso).
--     docker compose exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" "$POSTGRES_DB"' \
--         < ../DigitalmenuBackend/deploy/migrations/2026-11_home_delivery.sql
-- IDEMPOTENTE: si può rieseguire senza effetti.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS delivery_settings (
    id                          bigserial PRIMARY KEY,
    id_agency                   bigint       NOT NULL,
    enabled                     boolean      NOT NULL DEFAULT false,
    origin_lat                  double precision,
    origin_lng                  double precision,
    origin_address              varchar(300),
    zones                       text         NOT NULL DEFAULT '[]',
    free_delivery_over_cents    bigint,
    payment_mode                varchar(30)  NOT NULL DEFAULT 'ON_DELIVERY_ONLY',
    on_delivery_methods         text         NOT NULL DEFAULT '["CASH"]',
    slot_minutes                integer      NOT NULL DEFAULT 15,
    deliveries_per_slot         integer      NOT NULL DEFAULT 4,
    lead_minutes                integer      NOT NULL DEFAULT 30,
    weekly_hours                text,
    require_phone_otp_takeaway  boolean      NOT NULL DEFAULT false,
    created_at                  timestamp(6) with time zone NOT NULL DEFAULT now(),
    updated_at                  timestamp(6) with time zone NOT NULL DEFAULT now()
);

-- Colonne aggiunte dopo la prima versione della tabella (no-op se già presenti)
ALTER TABLE delivery_settings ADD COLUMN IF NOT EXISTS weekly_hours text;
ALTER TABLE delivery_settings ADD COLUMN IF NOT EXISTS require_phone_otp_takeaway boolean NOT NULL DEFAULT false;
ALTER TABLE delivery_settings ADD COLUMN IF NOT EXISTS free_delivery_over_cents bigint;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE tablename = 'delivery_settings' AND indexname = 'uk_delivery_settings_id_agency')
       AND NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid
                       WHERE t.relname = 'delivery_settings' AND c.contype = 'u') THEN
        CREATE UNIQUE INDEX uk_delivery_settings_id_agency ON delivery_settings (id_agency);
    END IF;
END $$;
