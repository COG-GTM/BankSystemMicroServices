-- One-off migration for databases created while hbm2ddl.auto=create generated ON DELETE CASCADE
-- foreign keys. hbm2ddl.auto=update does not rewrite existing constraints, so replace them with
-- RESTRICT so payments, adjustments, transfers and bills can no longer be erased by a parent delete.
DO $$
DECLARE
    fk RECORD;
BEGIN
    FOR fk IN
        SELECT con.conname, cls.relname AS table_name, att.attname AS column_name, ref.relname AS ref_table
        FROM pg_constraint con
        JOIN pg_class cls ON cls.oid = con.conrelid
        JOIN pg_class ref ON ref.oid = con.confrelid
        JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = con.conkey[1]
        JOIN pg_namespace nsp ON nsp.oid = cls.relnamespace
        WHERE con.contype = 'f'
          AND con.confdeltype = 'c'
          AND nsp.nspname = 'public'
          AND cls.relname IN ('payments', 'adjustments', 'transfers', 'bills')
    LOOP
        EXECUTE format('ALTER TABLE public.%I DROP CONSTRAINT %I', fk.table_name, fk.conname);
        EXECUTE format('ALTER TABLE public.%I ADD CONSTRAINT %I FOREIGN KEY (%I) REFERENCES public.%I (id) ON DELETE RESTRICT',
                       fk.table_name, fk.conname, fk.column_name, fk.ref_table);
    END LOOP;
END $$;

ALTER TABLE public.bills ADD COLUMN IF NOT EXISTS closed boolean NOT NULL DEFAULT false;
ALTER TABLE public.bills ADD COLUMN IF NOT EXISTS closed_at timestamp;
ALTER TABLE public.accounts ADD COLUMN IF NOT EXISTS closed boolean NOT NULL DEFAULT false;
ALTER TABLE public.accounts ADD COLUMN IF NOT EXISTS closed_at timestamp;
