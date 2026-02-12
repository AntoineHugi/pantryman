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
  const [isEditingName, setIsEditingName] = useState(false);
  const [editedName, setEditedName] = useState("");

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

  const handleEditName = () => {
    if (list) {
      setEditedName(list.name);
      setIsEditingName(true);
    }
  };

  const handleSaveName = async () => {
    if (!id || !editedName.trim()) return;

    try {
      await listsApi.update(id, editedName);
      setList(prev => prev ? { ...prev, name: editedName } : prev);
      setIsEditingName(false);
    } catch (err) {
      console.error("Failed to update list name", err);
      alert("Could not update list name");
    }
  };

  const handleCancelEdit = () => {
    setIsEditingName(false);
    setEditedName("");
  };

  if (error) return <p style={{ color: "red" }}>{error}</p>;
  if (!list) return <div className="loading">Loading...</div>;

  return (
    <div>
      <div className="page-title">
        {isEditingName ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', width: '100%' }}>
            <button className="btn btn-secondary back-btn-small" onClick={() => navigate(-1)}>
              ← Back
            </button>
            <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
              <input
                type="text"
                className="form-input"
                value={editedName}
                onChange={(e) => setEditedName(e.target.value)}
                autoFocus
                style={{ maxWidth: '400px' }}
              />
              <button className="btn btn-primary btn-small" onClick={handleSaveName}>
                Save
              </button>
              <button className="btn btn-secondary btn-small" onClick={handleCancelEdit}>
                Cancel
              </button>
            </div>
          </div>
        ) : (
          <div className="title-with-edit">
            <button className="btn btn-secondary back-btn-small" onClick={() => navigate(-1)}>
              ← Back
            </button>
            <div className="title-row">
              <h1>{list.name}</h1>
              <button className="edit-btn" onClick={handleEditName} title="Edit list name">
                <svg
                  width="20"
                  height="20"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                  <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
                </svg>
              </button>
            </div>
          </div>
        )}
      </div>

      <div className="section">
        <div className="section-header">
          <h2>Items</h2>
        </div>

        {list.items.length === 0 && !showForm && (
          <div className="empty-state">
            <p>No items in this list yet</p>
          </div>
        )}

        {list.items.length > 0 && (
          <ul className="items-list">
            {list.items.map((item) => (
              <li key={item.id} className="item-card">
                <div className="item-content">
                  <div
                    className="item-info"
                    onClick={() => toggleCheck(item)}
                    style={{
                      textDecoration: item.isChecked ? 'line-through' : 'none',
                      opacity: item.isChecked ? 0.6 : 1,
                    }}
                  >
                    <span className="item-name">{item.name}</span>
                    <span className="item-quantity">x{item.quantity}</span>
                  </div>
                  <div className="item-actions">
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        handleIncrease(item);
                      }}
                      title="Increase quantity"
                    >
                      +
                    </button>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        handleDecrease(item);
                      }}
                      title="Decrease quantity"
                    >
                      −
                    </button>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        handleDelete(item.id);
                      }}
                      title="Delete item"
                      style={{ color: '#dc3545' }}
                    >
                      ✕
                    </button>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      {!showForm && (
        <button className="btn btn-primary" onClick={() => setShowForm(true)}>
          + Add Item
        </button>
      )}
      {showForm && (
        <AddItemForm
          listId={list.id}
          onItemAdded={handleItemAdded}
          onCancel={() => setShowForm(false)}
        />
      )}
    </div>
  );
}

export default ListDetailsPage;