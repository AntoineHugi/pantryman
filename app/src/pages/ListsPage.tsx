import { useState } from "react";
import { Link } from "react-router-dom";
import { getErrorMessage } from "../api/client";
import { useCreateList, useDeleteList, useLists } from "../hooks/useGroceryLists";
import "../App.css";

function ListsPage() {
  const [showForm, setShowForm] = useState(false);
  const [newListName, setNewListName] = useState("");
  const { data: lists = [], isPending, error } = useLists();
  const createList = useCreateList();
  const deleteList = useDeleteList();

  const handleAddList = () => {
    if (!newListName.trim()) return alert("List name cannot be empty!");

    createList.mutate(newListName, {
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
          <ul className="card-list">
            {lists.map((list) => (
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