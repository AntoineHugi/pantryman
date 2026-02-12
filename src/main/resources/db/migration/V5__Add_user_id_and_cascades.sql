DELETE FROM item_list;
DELETE FROM grocery_lists;

ALTER TABLE grocery_lists
ADD COLUMN user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE item_list
ADD CONSTRAINT fk_item_list_grocery_list
FOREIGN KEY (list_id) REFERENCES grocery_lists(id) ON DELETE CASCADE;