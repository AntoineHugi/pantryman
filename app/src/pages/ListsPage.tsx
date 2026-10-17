import { useState } from "react";
import { Link } from "react-router-dom";
import { getErrorMessage } from "../api/client";
import type { GroceryList } from "../api/types";
import { useCreateList, useDeleteList, useLists } from "../hooks/useGroceryLists";
import { useHouseholds } from "../hooks/useHouseholds";
import "../App.css";

function ListsPage() {
  const [showForm, setShowForm] = useState(false);
  const [newListName, setNewListName] = useState("");
  // "" = personal; undefined = not chosen yet, which defaults to the first household
  const [newListHouseholdId, setNewListHouseholdId] = useState<string | undefined>(undefined);
  const { data: lists = [], isPending, error } = useLists();
  const { data: households = [] } = useHouseholds();
  const householdId = newListHouseholdId ?? households[0]?.id ?? "";
  const createList = useCreateList();
  const deleteList = useDeleteList();

  const handleAddList = () => {
    if (!newListName.trim()) return alert("List name cannot be empty!");

    createList.mutate({ name: newListName, householdId: householdId || null }, {
      onSuccess: () => {
        setNewListName("");
        setShowForm(false);
      },
      onError: (err) => alert(getErrorMessage(err, "Could not create list")),
    });
  };

  const handleDeleteList = (listId: string, e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();

    if (!confirm("Are you sure you want to delete this list?")) return;

    deleteList.mutate(listId, {
      onError: (err) => alert(getErrorMessage(err, "Could not delete list")),
    });
  };

  // Without households there is nothing to group by, so the page looks as it always did
  const groups: { title: string | null; lists: GroceryList[] }[] =
    households.length === 0
      ? [{ title: null, lists }]
      : [
          ...households.map((h) => ({ title: h.name, lists: lists.filter((l) => l.householdId === h.id) })),
          { title: "Personal", lists: lists.filter((l) => !l.householdId) },
          // Shared with a household the households query doesn't know about yet
          {
            title: "Shared",
            lists: lists.filter((l) => l.householdId && !households.some((h) => h.id === l.householdId)),
          },
        ].filter((g) => g.lists.length > 0);

  return (
    <div>
      <div className="page-header">
        <h2>Your Grocery Lists</h2>
      </div>

      {error && <p style={{ color: "red" }}>{getErrorMessage(error, "Could not load grocery lists")}</p>}

      {isPending && !error && <div className="loading">Loading...</div>}

      {isPending ? null : lists.length === 0 && !showForm ? (
        <div className="empty-state">
          <p>No grocery lists yet</p>
          <button className="btn btn-primary" onClick={() => setShowForm(true)}>
            Create Your First List
          </button>
        </div>
      ) : (
        <>
          {groups.map((group) => (
            <div key={group.title ?? "all"}>
              {group.title && <h3 className="list-group-title">{group.title}</h3>}
              <ul className="card-list">
                {group.lists.map((list) => (
                  <li key={list.id} className="card-list-item">
                    <Link className="list-link" to={`/lists/${list.id}/items`}>
                      {list.name}
                    </Link>
                    <button
                      className="delete-list-btn"
                      onClick={(e) => handleDeleteList(list.id, e)}
                      title="Delete list"
                    >
                      <svg
                        width="18"
                        height="18"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="2"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      >
                        <path d="M3 6h18" />
                        <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6" />
                        <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2" />
                        <line x1="10" y1="11" x2="10" y2="17" />
                        <line x1="14" y1="11" x2="14" y2="17" />
                      </svg>
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          ))}

          {!showForm && (
            <button className="btn btn-primary" onClick={() => setShowForm(true)}>
              + Add List
            </button>
          )}
        </>
      )}

      {showForm && (
        <div className="add-form">
          <div className="form-group">
            <input
              type="text"
              className="form-input"
              placeholder="Enter list name"
              value={newListName}
              onChange={(e) => setNewListName(e.target.value)}
              autoFocus
            />
          </div>
          {households.length > 0 && (
            <div className="form-group">
              <select
                className="form-input"
                value={householdId}
                onChange={(e) => setNewListHouseholdId(e.target.value)}
                aria-label="Share with"
              >
                {households.map((h) => (
                  <option key={h.id} value={h.id}>
                    Shared with {h.name}
                  </option>
                ))}
                <option value="">Personal (only me)</option>
              </select>
            </div>
          )}
          <div className="form-actions">
            <button className="btn btn-primary" onClick={handleAddList} disabled={createList.isPending}>
              Create
            </button>
            <button className="btn btn-secondary" onClick={() => setShowForm(false)}>
              Cancel
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

export default ListsPage;