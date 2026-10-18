-- Never used by the app: suggestions come from the item history kept on the device instead
ALTER TABLE item_list DROP COLUMN is_favorite;
