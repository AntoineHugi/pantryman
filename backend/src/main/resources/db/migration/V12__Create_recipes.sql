-- Recipes are shared with every user. Only the user who added one can edit or delete it;
-- if they delete their account, the recipe stays (created_by becomes NULL).
CREATE TABLE recipes
(
    id           UUID PRIMARY KEY,
    created_by   UUID REFERENCES users (id) ON DELETE SET NULL,
    name         VARCHAR(200) NOT NULL,
    servings     INT          NOT NULL,
    description  TEXT,
    prep_minutes INT,
    cook_minutes INT,
    source_url   VARCHAR(2000),
    notes        TEXT,
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_recipes_created_by ON recipes (created_by);

CREATE TABLE recipe_ingredients
(
    recipe_id UUID         NOT NULL REFERENCES recipes (id) ON DELETE CASCADE,
    position  INT          NOT NULL,
    name      VARCHAR(100) NOT NULL,
    -- NULL for "salt to taste"
    amount    NUMERIC(12, 3),
    -- NULL for a count ("2 eggs")
    unit      VARCHAR(16),
    note      VARCHAR(255),
    section   VARCHAR(100),
    optional  BOOLEAN      NOT NULL DEFAULT false,
    PRIMARY KEY (recipe_id, position)
);

CREATE TABLE recipe_steps
(
    recipe_id UUID NOT NULL REFERENCES recipes (id) ON DELETE CASCADE,
    position  INT  NOT NULL,
    text      TEXT NOT NULL,
    PRIMARY KEY (recipe_id, position)
);

CREATE TABLE recipe_tags
(
    recipe_id UUID        NOT NULL REFERENCES recipes (id) ON DELETE CASCADE,
    tag       VARCHAR(50) NOT NULL,
    PRIMARY KEY (recipe_id, tag)
);
