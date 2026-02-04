import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { listsApi } from "../api/lists";
import { itemsApi } from "../api/items";
import type { Item, GroceryList } from "../api/types";
import AddItemForm from "../components/AddItemForm";
import '../App.css';

function ListDetailsPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [list, setList] = useState<GroceryList | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);

  useEffect(() => {
    if (!id) return;
    listsApi
      .getById(id)
      .then((data) => setList(data))
      .catch((err) => {
        console.error("Failed to fetch list:", err);
        setError("Could not load grocery list");
      });
  }, [id]);

  const handleItemAdded = (newItem: Item) => {
    setList((prev) =>
      prev ? { ...prev, items: [...prev.items, newItem] } : prev
    );
    setShowForm(false);
  };

  const handleIncrease = async (item: Item) => {
    if (!id) return;

    try {
      await itemsApi.update(id, item.id, {
        quantity: item.quantity + 1,
      });

      setList(prev =>
        prev
          ? {
              ...prev,
              items: prev.items.map(it =>
                it.id === item.id ? { ...it, quantity: it.quantity + 1 } : it
              ),
            }
          : prev
      );
    } catch (err) {
      console.error("Failed to increase quantity", err);
    }
  };

  const handleDecrease = async (item: Item) => {
    if (!id) return;

    try {
      await itemsApi.update(id, item.id, {
        quantity: item.quantity - 1,
      });

      setList(prev =>
        prev
          ? {
              ...prev,
              items: prev.items.map(it =>
                it.id === item.id ? { ...it, quantity: it.quantity - 1 } : it
              ),
            }
          : prev
      );
    } catch (err) {
      console.error("Failed to decrease quantity", err);
    }
  };

  const handleDelete = async (itemId: string) => {
    if (!id) return;

    try {
      await itemsApi.delete(id, itemId);
      setList((prev) =>
        prev
          ? {
              ...prev,
              items: prev.items.filter((item) => item.id !== itemId),
            }
          : prev
      );
    } catch (err) {
      console.error("Failed to delete item", err);
    }
  };

  const toggleCheck = async (item: Item) => {
    if (!id) return;

    try {
      await itemsApi.update(id, item.id, {
        isChecked: !item.isChecked,
      });

      setList(prev =>
        prev
          ? {
              ...prev,
              items: prev.items.map(it =>
                it.id === item.id ? { ...it, isChecked: !it.isChecked } : it
              ),
            }
          : prev
      );
    } catch (err) {
      console.error("Failed to toggle check", err);
    }
  };

  const toggleFavorite = async (item: Item) => {
    if (!id) return;

    try {
      await itemsApi.update(id, item.id, {
        isFavorite: !item.isFavorite,
      });

      setList(prev =>
        prev
          ? {
              ...prev,
              items: prev.items.map(it =>
                it.id === item.id ? { ...it, isFavorite: !it.isFavorite } : it
              ),
            }
          : prev
      );
    } catch (err) {
      console.error("Failed to toggle favorite", err);
    }
  };

  if (error) return <p style={{ color: "red" }}>{error}</p>;
  if (!list) return <p>Loading...</p>;

  return (
    <>
      <h1>{list.name}</h1>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2>Items</h2>
        <button onClick={() => navigate(-1)}>← Back</button>
      </div>
      {list.items.length === 0 ? (
        <p>No items yet</p>
      ) : (
        <ul>
          {list.items.map((item) => (
            <li
              key={item.id}
              className="item-row"
              onClick={() => toggleCheck(item)}
              style={{
                textDecoration: item.isChecked ? 'line-through' : 'none',
                cursor: 'pointer',
              }}
            >
              <span>
                {item.name} (x{item.quantity})
              </span>
              <div className="item-actions">
                <button onClick={(e) => { e.stopPropagation(); toggleFavorite(item); }} style={{ color: item.isFavorite ? 'gold' : 'gray' }}>★</button>
                <button onClick={(e) => { e.stopPropagation(); handleIncrease(item); }}>+</button>
                <button onClick={(e) => { e.stopPropagation(); handleDecrease(item); }}>−</button>
                <button onClick={(e) => { e.stopPropagation(); handleDelete(item.id); }}>✕</button>
              </div>
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

export default ListDetailsPage;