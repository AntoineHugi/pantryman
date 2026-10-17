import { useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { getErrorMessage } from "../api/client";
import type { Item } from "../api/types";
import AddItemForm from "../components/AddItemForm";
import { useAuth } from "../hooks/useAuth";
import { useDeleteItem, useList, useRenameList, useSetListHousehold, useUpdateItem } from "../hooks/useGroceryLists";
import { useHouseholds } from "../hooks/useHouseholds";
import '../App.css';

function ListDetailsPage() {
  const { id = "" } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [showForm, setShowForm] = useState(false);
  const [isEditingName, setIsEditingName] = useState(false);
  const [editedName, setEditedName] = useState("");
  const [editingItemId, setEditingItemId] = useState<string | null>(null);
  const [editedItemName, setEditedItemName] = useState("");
  const [editedItemQuantity, setEditedItemQuantity] = useState(1);

  const { data: list, error } = useList(id);
  const updateItem = useUpdateItem(id);
  const deleteItem = useDeleteItem(id);
  const renameList = useRenameList(id);
  const setListHousehold = useSetListHousehold(id);
  const { data: households = [] } = useHouseholds();
  const { userId } = useAuth();
  // Item changes show immediately and roll back on failure; surface the last failure
  const actionError = updateItem.error ?? deleteItem.error;

  const handleIncrease = (item: Item) => {
    updateItem.mutate({ itemId: item.id, data: { quantity: item.quantity + 1 } });
  };

  const handleDecrease = (item: Item) => {
    if (item.quantity <= 1) return;
    updateItem.mutate({ itemId: item.id, data: { quantity: item.quantity - 1 } });
  };

  const handleDelete = (itemId: string) => {
    deleteItem.mutate(itemId);
  };

  const toggleCheck = (item: Item) => {
    updateItem.mutate({ itemId: item.id, data: { isChecked: !item.isChecked } });
  };

  const handleEditItem = (item: Item) => {
    setEditingItemId(item.id);
    setEditedItemName(item.name);
    setEditedItemQuantity(item.quantity);
  };

  const handleSaveItem = (itemId: string) => {
    if (!editedItemName.trim()) return;

    updateItem.mutate({ itemId, data: { name: editedItemName, quantity: editedItemQuantity } });
    setEditingItemId(null);
  };

  const handleCancelEditItem = () => {
    setEditingItemId(null);
    setEditedItemName("");
    setEditedItemQuantity(1);
  };

  const handleEditName = () => {
    if (list) {
      setEditedName(list.name);
      setIsEditingName(true);
    }
  };

  const handleSaveName = () => {
    if (!editedName.trim()) return;

    renameList.mutate(editedName, {
      onSuccess: () => setIsEditingName(false),
      onError: (err) => alert(getErrorMessage(err, "Could not update list name")),
    });
  };

  const handleCancelEdit = () => {
    setIsEditingName(false);
    setEditedName("");
  };

  const handleShareChange = (householdId: string) => {
    setListHousehold.mutate(householdId || null, {
      onError: (err) => alert(getErrorMessage(err, "Could not change who the list is shared with")),
    });
  };

  if (error) return <p style={{ color: "red" }}>{getErrorMessage(error, "Could not load grocery list")}</p>;
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

      {list.userId === userId && households.length > 0 ? (
        <div className="form-group list-sharing">
          <select
            className="form-input"
            value={list.householdId ?? ""}
            onChange={(e) => handleShareChange(e.target.value)}
            disabled={setListHousehold.isPending}
            aria-label="Share with"
          >
            <option value="">Personal (only me)</option>
            {households.map((h) => (
              <option key={h.id} value={h.id}>
                Shared with {h.name}
              </option>
            ))}
          </select>
        </div>
      ) : (
        list.householdId && (
          <p className="muted list-sharing">
            Shared with {households.find((h) => h.id === list.householdId)?.name ?? "your household"}
          </p>
        )
      )}

      <div className="section">
        <div className="section-header">
          <h2>Items</h2>
        </div>

        {actionError && (
          <p style={{ color: "red" }}>{getErrorMessage(actionError, "Could not update item")}</p>
        )}

        {list.items.length === 0 && !showForm && (
          <div className="empty-state">
            <p>No items in this list yet</p>
          </div>
        )}

        {list.items.length > 0 && (
          <ul className="items-list">
            {list.items.map((item) => (
              <li key={item.id} className="item-card">
                {editingItemId === item.id ? (
                  <div className="item-edit-form">
                    <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', flexWrap: 'wrap' }}>
                      <input
                        type="text"
                        className="form-input"
                        value={editedItemName}
                        onChange={(e) => setEditedItemName(e.target.value)}
                        autoFocus
                        style={{ flex: 1, minWidth: '100px' }}
                      />
                      <input
                        type="number"
                        className="form-input"
                        min={1}
                        value={editedItemQuantity}
                        onChange={(e) => setEditedItemQuantity(Number(e.target.value))}
                        style={{ width: '60px' }}
                      />
                      <button className="btn btn-primary btn-small" onClick={() => handleSaveItem(item.id)}>
                        Save
                      </button>
                      <button className="btn btn-secondary btn-small" onClick={handleCancelEditItem}>
                        Cancel
                      </button>
                    </div>
                  </div>
                ) : (
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
                          handleEditItem(item);
                        }}
                        title="Edit item"
                      >
                        ✎
                      </button>
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
                )}
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
          onItemAdded={() => setShowForm(false)}
          onCancel={() => setShowForm(false)}
        />
      )}
    </div>
  );
}

export default ListDetailsPage;