-- ─────────────────────────────────────────────────────────────────────────────
-- Gruppi di varianti del prodotto (novembre 2026) — vedi docs/VARIANTI_PRODOTTO.md
--
-- products.variant_groups: JSON dei gruppi (impasto, mozzarella, ...) oltre al formato; null = nessun gruppo.
-- menu_translations.field: da varchar(16) a varchar(128), perché le traduzioni dei nomi di gruppi e scelte
--   usano come campo l'id del gruppo ("impasto") o gruppo/scelta ("impasto/senza-glutine").
--
-- Con spring.jpa.hibernate.ddl-auto=update la colonna nuova la crea già Hibernate all'avvio, ma Hibernate
-- NON allarga le colonne esistenti: l'ALTER su menu_translations.field serve comunque.
--
-- QUANDO: prima o subito dopo il deploy della versione nuova (può girare con il backend acceso).
--     docker compose exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" "$POSTGRES_DB"' \
--         < ../DigitalmenuBackend/deploy/migrations/2026-11_product_variant_groups.sql
-- IDEMPOTENTE: si può rieseguire senza effetti.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE products ADD COLUMN IF NOT EXISTS variant_groups text;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'menu_translations' AND column_name = 'field'
                 AND character_maximum_length IS NOT NULL AND character_maximum_length < 128) THEN
        ALTER TABLE menu_translations ALTER COLUMN field TYPE varchar(128);
    END IF;
END $$;
