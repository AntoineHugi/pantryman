import { useState } from "react";
import api from "./api";

type AddItemFormProps = {
  listId: string;
  onItemAdded: (newItem: Item) => void;
};

type Item = {
  id: string;
  name: string;
  quantity: number;
  isChecked: boolean;
  isFavorite: boolean;
};

function AddItemForm({ listId, onItemAdded }: AddItemFormProps) {
  const [itemName, setItemName] = useState("");
  const [quantity, setQuantity] = useState(1);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    try {
      const payload = { listId, name: itemName, quantity };

      const res = await api.post<Item>(`/lists/${listId}/items`, payload);
      onItemAdded(res.data);

      setItemName("");
      setQuantity(1);
    } catch (err) {
      console.error("Failed to add item:", err);
      setError("Could not add item. Check payload format.");
    }
  };

  return (
    <form onSubmit={handleSubmit} style={{ marginTop: "1rem" }}>
      <input
        type="text"
        placeholder="Item name"
        value={itemName}
        onChange={(e) => setItemName(e.target.value)}
        required
      />
      <input
        type="number"
        min={1}
        value={quantity}
        onChange={(e) => setQuantity(Number(e.target.value))}
        required
      />
      <button type="submit">Add</button>
      {error && <p style={{ color: "red" }}>{error}</p>}
    </form>
  );
}

export default AddItemForm