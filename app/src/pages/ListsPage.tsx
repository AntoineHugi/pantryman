import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { getErrorMessage } from "../api/client";
import type { GroceryList } from "../api/types";
import SlowServerNotice from "../components/SlowServerNotice";
import { useCreateList, useLists } from "../hooks/useGroceryLists";
import { useHouseholds } from "../hooks/useHouseholds";
import { usePantry } from "../hooks/usePantry";
import { formatItemQuantity } from "../utils/itemInput";
import "../App.css";

function listSummary(list: GroceryList) {
  if (list.items.length === 0) return "Empty";
  const left = list.items.filter((item) => !item.isChecked).length;
  if (left === 0) return "All done";
  return `${left} ${left === 1 ? "item" : "items"} left`;
}

function ListsPage() {
  const navigate = useNavigate();
  const [showForm, setShowForm] = useState(false);
  const [newListName, setNewListName] = useState("");
  // "" = personal; undefined = not chosen yet, which defaults to the first household
  const [newListHouseholdId, setNewListHouseholdId] = useState<string | undefined>(undefined);
  const { data: lists = [], isPending, error } = useLists();
  const { data: households = [], isSuccess: householdsLoaded } = useHouseholds();
  const householdId = newListHouseholdId ?? households[0]?.id ?? "";
  const createList = useCreateList();
  // The usual items of the new list's household (or the user's own): all ticked to start with
  const { data: pantry = [] } = usePantry(householdId || null, showForm);
  const defaults = pantry.filter((item) => item.kind === "default");
  const [leftOut, setLeftOut] = useState<Set<string>>(new Set());
  const toggleDefault = (id: string) =>
    setLeftOut((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  const handleAddList = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newListName.trim()) return;

    const defaultItemIds = defaults.filter((item) => !leftOut.has(item.id)).map((item) => item.id);
    createList.mutate({ name: newListName.trim(), householdId: householdId || null, defaultItemIds }, {
      // Straight into the new list, ready to add items
      onSuccess: (list) => navigate(`/lists/${list.id}/items`),
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

  const form = (
    <form className="add-form" onSubmit={handleAddList}>
      <div className="form-group">
        <input
          type="text"
          className="form-input"
          placeholder="List name, e.g. Weekly shop"
          aria-label="List name"
          value={newListName}
          onChange={(e) => setNewListName(e.target.value)}
          maxLength={100}
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
      {defaults.length > 0 ? (
        <div className="form-group">
          <span className="form-label">Start with</span>
          <ul className="ingredient-checklist">
            {defaults.map((item) => (
              <li key={item.id}>
                <label>
                  <input type="checkbox" checked={!leftOut.has(item.id)} onChange={() => toggleDefault(item.id)} />
                  <span className="pantry-name">{item.name}</span>
                  <span className="item-quantity">{formatItemQuantity(item)}</span>
                </label>
              </li>
            ))}
          </ul>
        </div>
      ) : (
        <p className="form-hint">
          Tip: <Link to="/pantry">usual items</Link> like milk can go on every new list.
        </p>
      )}
      {createList.isError && <p className="form-error">{getErrorMessage(createList.error, "Could not create list")}</p>}
      <div className="form-actions">
        <button type="submit" className="btn btn-primary" disabled={createList.isPending || !newListName.trim()}>
          Create
        </button>
        <button type="button" className="btn btn-secondary" onClick={() => setShowForm(false)}>
          Cancel
        </button>
      </div>
    </form>
  );

  return (
    <div>
      <div className="page-header">
        <h1>Your lists</h1>
      </div>

      {error && lists.length === 0 && <p className="form-error">{getErrorMessage(error, "Could not load grocery lists")}</p>}
      {error && lists.length > 0 && (
        <p className="offline-banner">Can't reach the server. Showing the last saved copy.</p>
      )}

      {isPending && (
        <>
          <div className="loading">Loading…</div>
          <SlowServerNotice active />
        </>
      )}

      {!isPending && lists.length === 0 && !showForm && !error && (
        <div className="empty-state">
          <p>No grocery lists yet</p>
          <button className="btn btn-primary" onClick={() => setShowForm(true)}>
            Create your first list
          </button>
        </div>
      )}

      {groups.map((group) =>
        group.lists.length === 0 ? null : (
          <div key={group.title ?? "all"}>
            {group.title && <h2 className="list-group-title">{group.title}</h2>}
            <ul className="card-list">
              {group.lists.map((list) => (
                <li key={list.id}>
                  <Link className="list-card" to={`/lists/${list.id}/items`}>
                    <span className="list-card-name">{list.name}</span>
                    <span className="list-card-summary">{listSummary(list)}</span>
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        )
      )}

      {showForm
        ? form
        : lists.length > 0 && (
            <button className="btn btn-primary" onClick={() => setShowForm(true)}>
              + New list
            </button>
          )}

      {householdsLoaded && households.length === 0 && !isPending && (
        <p className="household-hint">
          Shopping with someone? <Link to="/households">Create a household</Link> to share lists with them.
        </p>
      )}
    </div>
  );
}

export default ListsPage;
