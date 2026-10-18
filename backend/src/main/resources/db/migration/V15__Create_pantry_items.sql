-- A household's (or one user's) usual items: "default" ones are added to every new list,
-- "staple" ones are always at home, so recipes and meal plans skip them.
CREATE TABLE pantry_items
(
    id           UUID PRIMARY KEY,
    -- Exactly one of these: a personal item, or a household's
    user_id      UUID REFERENCES users (id) ON DELETE CASCADE,
    household_id UUID REFERENCES households (id) ON DELETE CASCADE,
    name         VARCHAR(100)   NOT NULL,
    -- 'default' or 'staple'
    kind         VARCHAR(10)    NOT NULL,
    -- For defaults: what goes on the list
    quantity     NUMERIC(12, 3) NOT NULL DEFAULT 1,
    unit         VARCHAR(16),
    created_at   TIMESTAMP      NOT NULL DEFAULT now(),
    CHECK ((user_id IS NULL) <> (household_id IS NULL))
);

CREATE INDEX idx_pantry_items_user ON pantry_items (user_id);
CREATE INDEX idx_pantry_items_household ON pantry_items (household_id);
