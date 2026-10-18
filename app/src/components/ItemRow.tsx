import { useState } from 'react';
import type { Item } from '../api/types';
import { formatItemQuantity, validQuantity } from '../utils/itemInput';
import { UNITS } from '../utils/recipeText';
import { PencilIcon } from './icons';

export type ItemChanges = { name: string; quantity: number; unit: string | null };

type ItemRowProps = {
  item: Item;
  isEditing: boolean;
  onToggle: () => void;
  onEdit: () => void;
  onSave: (changes: ItemChanges) => void;
  onCancel: () => void;
  onDelete: () => void;
};

// The whole row ticks the item; changing the name or quantity, and deleting, are in the editor
export default function ItemRow({ item, isEditing, onToggle, onEdit, onSave, onCancel, onDelete }: ItemRowProps) {
  if (isEditing) {
    return <ItemEditor item={item} onSave={onSave} onCancel={onCancel} onDelete={onDelete} />;
  }

  const quantity = formatItemQuantity(item);
  return (
    <li className={`item-row${item.isChecked ? ' is-checked' : ''}`}>
      <label className="item-check">
        <input type="checkbox" checked={item.isChecked} onChange={onToggle} />
        <span className="item-text">
          <span className="item-name">{item.name}</span>
          {item.fromRecipes && <span className="item-source">for {item.fromRecipes}</span>}
        </span>
        {quantity && <span className="item-quantity">{quantity}</span>}
      </label>
      <button className="icon-btn" onClick={onEdit} aria-label={`Edit ${item.name}`}>
        <PencilIcon />
      </button>
    </li>
  );
}

function ItemEditor({
  item,
  onSave,
  onCancel,
  onDelete,
}: Pick<ItemRowProps, 'item' | 'onSave' | 'onCancel' | 'onDelete'>) {
  const [name, setName] = useState(item.name);
  // Kept as text so the field can be empty while typing
  const [quantityText, setQuantityText] = useState(String(item.quantity));
  const [unit, setUnit] = useState(item.unit ?? '');
  const quantity = validQuantity(quantityText);
  const canSave = !!name.trim() && quantity !== null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (canSave) onSave({ name: name.trim(), quantity, unit: unit || null });
  };

  // Whole steps; "−" stops at 1 (type 0.5 for less)
  const step = (delta: number) => {
    const next = Math.round(((quantity ?? 1) + delta) * 1000) / 1000;
    if (next >= 1 && next <= 100_000) setQuantityText(String(next));
  };

  return (
    <li className="item-row is-editing">
      <form className="item-editor" onSubmit={handleSubmit}>
        <input
          type="text"
          className="form-input"
          value={name}
          onChange={(e) => setName(e.target.value)}
          aria-label="Item name"
          maxLength={200}
          autoFocus
        />
        <div className="item-editor-row">
          <div className="stepper">
            <button type="button" onClick={() => step(-1)} disabled={quantity === null || quantity < 2} aria-label="Less">
              −
            </button>
            <input
              type="text"
              inputMode="decimal"
              className="form-input"
              value={quantityText}
              onChange={(e) => setQuantityText(e.target.value)}
              aria-label="Quantity"
              aria-invalid={quantity === null}
            />
            <button type="button" onClick={() => step(1)} disabled={quantity !== null && quantity >= 100_000} aria-label="More">
              +
            </button>
          </div>
          <select className="form-input unit-select" value={unit} onChange={(e) => setUnit(e.target.value)} aria-label="Unit">
            <option value="">—</option>
            {UNITS.map((u) => (
              <option key={u} value={u}>
                {u}
              </option>
            ))}
          </select>
        </div>
        {quantity === null && <p className="form-error">Quantity must be a number above 0, like 2 or 0.5.</p>}
        <div className="form-actions item-editor-actions">
          <button type="submit" className="btn btn-primary btn-small" disabled={!canSave}>
            Save
          </button>
          <button type="button" className="btn btn-secondary btn-small" onClick={onCancel}>
            Cancel
          </button>
          <button type="button" className="btn btn-danger-outline btn-small" onClick={onDelete}>
            Delete
          </button>
        </div>
      </form>
    </li>
  );
}
