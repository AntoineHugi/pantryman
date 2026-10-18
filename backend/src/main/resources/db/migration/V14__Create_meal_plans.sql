-- A meal plan covers a few days (lunch and dinner). Like a list, it is personal or shared with a
-- household, and confirming it creates a grocery list from its recipes.
CREATE TABLE meal_plans
(
    id           UUID PRIMARY KEY,
    user_id      UUID      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    household_id UUID REFERENCES households (id) ON DELETE SET NULL,
    start_date   DATE      NOT NULL,
    days         INT       NOT NULL,
    -- Default servings for a new meal
    people       INT       NOT NULL,
    -- The list made on confirming; NULL before that, or once the list is deleted
    list_id      UUID REFERENCES grocery_lists (id) ON DELETE SET NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_meal_plans_user ON meal_plans (user_id);
CREATE INDEX idx_meal_plans_household ON meal_plans (household_id);

CREATE TABLE meal_plan_entries
(
    plan_id   UUID        NOT NULL REFERENCES meal_plans (id) ON DELETE CASCADE,
    date      DATE        NOT NULL,
    -- 'lunch' or 'dinner'
    meal      VARCHAR(10) NOT NULL,
    recipe_id UUID        NOT NULL REFERENCES recipes (id) ON DELETE CASCADE,
    servings  INT         NOT NULL,
    PRIMARY KEY (plan_id, date, meal)
);
