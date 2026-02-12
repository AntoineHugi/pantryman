import { useState } from "react";
import apiClient from '../api/client';

type AddItemFormProps = {
  listId: string;
  onItemAdded: (newItem: Item) => void;
  onCancel: () => void;
};

type Item = {
  id: string;
  name: string;
  quantity: number;
  isChecked: boolean;
  isFavorite: boolean;
};

function AddItemForm({ listId, onItemAdded, onCancel }: AddItemFormProps) {
  const [itemName, setItemName] = useState("");
  const [quantity, setQuantity] = useState(1);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    try {
      const payload = { listId, name: itemName, quantity };

      const res = await apiClient.post<Item>(`/lists/${listId}/items`, payload);
      onItemAdded(res.data);

      setItemName("");
      setQuantity(1);
    } catch (err) {
      console.error("Failed to add item:", err);
      setError("Could not add item. Check payload format.");
    }
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
        {error && <p style={{ color: "red", marginBottom: "1rem" }}>{error}</p>}
        <div className="form-actions">
          <button type="submit" className="btn btn-primary">
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