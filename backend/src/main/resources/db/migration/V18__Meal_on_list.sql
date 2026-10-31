-- A plan keeps one grocery list while days come and go: on_list marks the meals already on it, so
-- only new ones are added. Meals of plans that have a list were put on it when it was made.
ALTER TABLE meal_plan_entries
    ADD COLUMN on_list BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE meal_plan_entries
SET on_list = TRUE
WHERE recipe_id IS NOT NULL
  AND plan_id IN (SELECT id FROM meal_plans WHERE list_id IS NOT NULL);
