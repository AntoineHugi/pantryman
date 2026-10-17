-- Households let several users share grocery lists (and later recipes and meal plans).
CREATE TABLE households
(
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    invite_code VARCHAR(16)  NOT NULL UNIQUE,
    -- Can rename the household and remove members. Passes to another member when the owner leaves.
    owner_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE household_members
(
    household_id UUID      NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    user_id      UUID      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    joined_at    TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (household_id, user_id)
);

CREATE INDEX idx_household_members_user ON household_members (user_id);

-- NULL = personal list, visible to its owner only.
-- A deleted household's lists fall back to being personal lists of their owners.
ALTER TABLE grocery_lists
    ADD COLUMN household_id UUID REFERENCES households (id) ON DELETE SET NULL;

CREATE INDEX idx_grocery_lists_household ON grocery_lists (household_id);
