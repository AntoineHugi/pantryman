import { useState } from "react";
import { getErrorMessage } from '../api/client';
import { useAddItem } from '../hooks/useGroceryLists';

type AddItemFormProps = {
  listId: string;
  onItemAdded: () => void;
  onCancel: () => void;
};

function AddItemForm({ listId, onItemAdded, onCancel }: AddItemFormProps) {
  const [itemName, setItemName] = useState("");
  const [quantity, setQuantity] = useState(1);
  const addItem = useAddItem(listId);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    addItem.mutate(
      { name: itemName, quantity },
      {
        onSuccess: () => {
          setItemName("");
          setQuantity(1);
          onItemAdded();
        },
      }
    );
  };

  return (
    <div className="add-form">
      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <input
            type="text"
            className="form-input"
            placeholder="Item name"
            value={itemName}
            onChange={(e) => setItemName(e.target.value)}
            required
            autoFocus
          />
        </div>
        <div className="form-group">
          <input
            type="number"
            className="form-input"
            min={1}
            placeholder="Quantity"
            value={quantity}
            onChange={(e) => setQuantity(Number(e.target.value))}
            required
          />
        </div>
        {addItem.isError && (
          <p style={{ color: "red", marginBottom: "1rem" }}>
            {getErrorMessage(addItem.error, "Could not add item")}
          </p>
        )}
        <div className="form-actions">
          <button type="submit" className="btn btn-primary" disabled={addItem.isPending}>
            Add Item
          </button>
          <button type="button" className="btn btn-secondary" onClick={onCancel}>
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
}

export default AddItemForm
