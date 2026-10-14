-- Postgres has no stable row order without ORDER BY; sort lists and items by creation time.
ALTER TABLE grocery_lists
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT now();

ALTER TABLE item_list
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT now();
