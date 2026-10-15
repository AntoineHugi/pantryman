-- V3 added these columns as nullable; the Exposed ItemTable declares them NOT NULL.
-- Items without a list can't be reached through the API, so drop them.
DELETE FROM item_list WHERE list_id IS NULL;
UPDATE item_list SET quantity = 1 WHERE quantity IS NULL;
UPDATE item_list SET is_checked = FALSE WHERE is_checked IS NULL;
UPDATE item_list SET is_favorite = FALSE WHERE is_favorite IS NULL;

ALTER TABLE item_list
    ALTER COLUMN list_id SET NOT NULL,
    ALTER COLUMN quantity SET NOT NULL,
    ALTER COLUMN is_checked SET NOT NULL,
    ALTER COLUMN is_favorite SET NOT NULL;
