-- Recipes add items like "500 g flour" or "0.5 l milk"
ALTER TABLE item_list ALTER COLUMN quantity TYPE NUMERIC(12, 3);
-- NULL for a count ("3 eggs"); otherwise one of the recipe units
ALTER TABLE item_list ADD COLUMN unit VARCHAR(16);
-- Names of the recipes the item was added for, e.g. "Bolognese, Lasagna"
ALTER TABLE item_list ADD COLUMN from_recipes VARCHAR(500);
