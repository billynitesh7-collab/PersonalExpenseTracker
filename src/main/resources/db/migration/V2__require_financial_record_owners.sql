DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM transactions WHERE user_id IS NULL)
       OR EXISTS (SELECT 1 FROM budgets WHERE user_id IS NULL) THEN
        RAISE EXCEPTION 'Cannot apply ownership migration: transactions or budgets contain unowned records. Assign each record to its verified owner before retrying.';
    END IF;
END;
$$;

ALTER TABLE transactions ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE budgets ALTER COLUMN user_id SET NOT NULL;

DROP INDEX IF EXISTS uq_budgets_unowned_month;
