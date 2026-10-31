-- A meal is a recipe or free text ("Leftovers", "At grandma's"). Free text adds nothing to a grocery list.
ALTER TABLE meal_plan_entries
    ALTER COLUMN recipe_id DROP NOT NULL;
ALTER TABLE meal_plan_entries
    ADD COLUMN text VARCHAR(200);
ALTER TABLE meal_plan_entries
    ADD CONSTRAINT meal_plan_entries_recipe_or_text CHECK ((recipe_id IS NULL) <> (text IS NULL));
