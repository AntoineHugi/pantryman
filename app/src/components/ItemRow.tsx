import { useState } from 'react';
import type { Item } from '../api/types';
import { validQuantity } from '../utils/itemInput';
import { PencilIcon } from './icons';

type ItemRowProps = {
  item: Item;
  isEditing: boolean;
  onToggle: () => void;
  onEdit: () => void;
  onSave: (changes: { name: string; quantity: number }) => void;
  onCancel: () => void;
  onDelete: () => void;
};

// The whole row ticks the item; changing the name or quantity, and deleting, are in the editor
export default function ItemRow({ item, isEditing, onToggle, onEdit, onSave, onCancel, onDelete }: ItemRowProps) {
  if (isEditing) {
    return <ItemEditor item={item} onSave={onSave} onCancel={onCancel} onDelete={onDelete} />;
  }

  return (
    <li className={`item-row${item.isChecked ? ' is-checked' : ''}`}>
      <label className="item-check">
        <input type="checkbox" checked={item.isChecked} onChange={onToggle} />
        <span className="item-name">{item.name}</span>
        {item.quantity > 1 && <span className="item-quantity">×{item.quantity}</span>}
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
  const quantity = validQuantity(quantityText);
  const canSave = !!name.trim() && quantity !== null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (canSave) onSave({ name: name.trim(), quantity });
  };

  const step = (delta: number) => {
    const next = (quantity ?? 1) + delta;
    if (next >= 1 && next <= 999) setQuantityText(String(next));
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
            <button type="button" onClick={() => step(-1)} disabled={quantity === null || quantity <= 1} aria-label="Less">
              −
            </button>
            <input
              type="text"
              inputMode="numeric"
              className="form-input"
              value={quantityText}
              onChange={(e) => setQuantityText(e.target.value)}
              aria-label="Quantity"
              aria-invalid={quantity === null}
            />
            <button type="button" onClick={() => step(1)} disabled={quantity !== null && quantity >= 999} aria-label="More">
              +
            </button>
          </div>
          <button type="button" className="btn btn-danger-outline btn-small" onClick={onDelete}>
            Delete
          </button>
        </div>
        {quantity === null && <p className="form-error">Quantity must be a whole number from 1 to 999.</p>}
        <div className="form-actions">
          <button type="submit" className="btn btn-primary btn-small" disabled={!canSave}>
            Save
          </button>
          <button type="button" className="btn btn-secondary btn-small" onClick={onCancel}>
            Cancel
          </button>
        </div>
      </form>
    </li>
  );
}
