import { useRef, useState } from 'react';
import { getErrorMessage } from '../api/client';
import type { GroceryList } from '../api/types';
import { useAuth } from '../hooks/useAuth';
import { useFeedback } from '../hooks/useFeedback';
import { useAddItem, useUpdateItem } from '../hooks/useGroceryLists';
import { useItemSuggestions } from '../hooks/useItemSuggestions';
import { recordItemAdded } from '../utils/itemHistory';
import { findSameItem, formatItemQuantity, parseItemInput, type ParsedItem } from '../utils/itemInput';

// Always-visible input at the top of a list. Enter adds the item and keeps the keyboard open,
// so several items go in quickly. "3 eggs" or "500 g flour" sets the quantity.
export default function AddItemBar({ list }: { list: GroceryList }) {
  const [text, setText] = useState('');
  const [isFocused, setIsFocused] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const addItem = useAddItem(list.id);
  const updateItem = useUpdateItem(list.id);
  const { userId } = useAuth();
  const { toast } = useFeedback();
  const parsed = parseItemInput(text);
  const suggestions = useItemSuggestions(list.items, parsed.name);
  const showSuggestions = isFocused && suggestions.length > 0;

  const onError = (err: unknown) => toast({ message: getErrorMessage(err, 'Could not add the item'), isError: true });

  const add = ({ name, quantity, unit }: ParsedItem) => {
    if (!name) return;
    const existing = findSameItem(list.items, name);
    const sameUnit = existing && (existing.unit ?? null) === unit;
    if (existing && !existing.isChecked && sameUnit) {
      const newQuantity = Math.round((existing.quantity + quantity) * 1000) / 1000;
      updateItem.mutate({ itemId: existing.id, data: { quantity: newQuantity } }, { onError });
      toast({
        message: `${existing.name} was already on the list. It's now ${formatItemQuantity({ quantity: newQuantity, unit }) || 1}.`,
      });
    } else if (existing && !existing.isChecked && !unit) {
      // "milk" when "milk 1 l" (from a recipe) is on the list: it's there already
      toast({ message: `${existing.name} is already on the list (${formatItemQuantity(existing)}).` });
    } else if (existing && existing.isChecked) {
      // Ticked earlier: put it back on the list rather than adding a second one
      updateItem.mutate({ itemId: existing.id, data: { isChecked: false, quantity, unit } }, { onError });
    } else {
      // New, or the same name in another unit ("2 cups flour" next to "500 g flour")
      addItem.mutate({ name, quantity, unit }, { onError });
    }
    recordItemAdded(userId, name);
    setText('');
    inputRef.current?.focus();
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    add(parsed);
  };

  // Tapping a button would otherwise take focus from the input and close the phone's keyboard
  const keepFocus = (e: React.PointerEvent) => e.preventDefault();

  return (
    <div className="add-item-bar">
      <form className="add-item-form" onSubmit={handleSubmit}>
        <input
          ref={inputRef}
          type="text"
          className="form-input"
          placeholder="Add an item, e.g. 2 milk"
          aria-label="Add an item"
          value={text}
          onChange={(e) => setText(e.target.value)}
          onFocus={() => setIsFocused(true)}
          onBlur={() => setIsFocused(false)}
          enterKeyHint="done"
          autoComplete="off"
          maxLength={200}
        />
        <button type="submit" className="btn btn-primary" onPointerDown={keepFocus} disabled={!parsed.name}>
          Add
        </button>
      </form>
      {showSuggestions && (
        <div className="suggestions" aria-label={parsed.name ? 'Suggestions' : 'Usual items'}>
          {suggestions.map((name) => (
            <button
              key={name}
              type="button"
              className="suggestion-chip"
              onPointerDown={keepFocus}
              onClick={() => add(parsed.name ? { ...parsed, name } : { name, quantity: 1, unit: null })}
            >
              + {name}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
