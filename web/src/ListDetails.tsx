import { useEffect, useState } from "react";
import { Link, Routes, Route, useParams, Navigate } from "react-router-dom";
import './App.css'
import api from "./api";
import AddItemForm from "./AddItemForm";

type Item = {
  id: string;
  name: string;
  quantity: number;
  isChecked: boolean;
  isFavorite: boolean;
};

type GroceryList = {
  id: string;
  name: string;
  items: { id: string; name: string; quantity: number }[];
};

function ListDetails() {
  const { id } = useParams<{ id: string }>();
  const [list, setList] = useState<GroceryList | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);

  useEffect(() => {
    if (!id) return;
    api
      .get<GroceryList>(`/lists/${id}`)
      .then((res) => setList(res.data))
      .catch((err) => {
        console.error("Failed to fetch list:", err);
        setError("Could not load grocery list");
      });
  }, [id]);

  const handleItemAdded = (newItem: Item) => {
    setList((prev) =>
      prev ? { ...prev, items: [...prev.items, newItem] } : prev
    );
  };

  if (error) return <p style={{ color: "red" }}>{error}</p>;
  if (!list) return <p>Loading...</p>; // <-- prevent accessing list before it exists

  return (
    <>
      <h1>{list.name}</h1>
      <h2>Items</h2>
      {list.items.length === 0 ? (
        <p>No items yet</p>
      ) : (
        <ul>
          {list.items.map((item) => (
            <li key={item.id} className={item.isChecked ? "checked-item" : ""}>
              {item.name} (x{item.quantity})
            </li>
          ))}
        </ul>
      )}

      {!showForm && <button onClick={() => setShowForm(true)}>Add item</button>}
      {showForm && (
        <AddItemForm listId={list.id} onItemAdded={handleItemAdded} />
      )}
    </>
  );
}

export default ListDetails