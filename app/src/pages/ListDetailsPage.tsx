import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { getErrorMessage } from "../api/client";
import type { Item } from "../api/types";
import AddItemBar from "../components/AddItemBar";
import BackLink from "../components/BackLink";
import ItemRow, { type ItemChanges } from "../components/ItemRow";
import SlowServerNotice from "../components/SlowServerNotice";
import { ChevronIcon, PencilIcon } from "../components/icons";
import { useAuth } from "../hooks/useAuth";
import { useFeedback } from "../hooks/useFeedback";
import {
  useDeleteItem,
  useDeleteItems,
  useDeleteList,
  useList,
  useRenameList,
  useRestoreItems,
  useSetListHousehold,
  useUpdateItem,
} from "../hooks/useGroceryLists";
import { useHouseholds } from "../hooks/useHouseholds";
import '../App.css';

function ListDetailsPage() {
  const { id = "" } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [isEditingName, setIsEditingName] = useState(false);
  const [editedName, setEditedName] = useState("");
  const [editingItemId, setEditingItemId] = useState<string | null>(null);
  const [showChecked, setShowChecked] = useState(true);

  const { data: list, error, isPending } = useList(id);
  const updateItem = useUpdateItem(id);
  const deleteItem = useDeleteItem(id);
  const deleteItems = useDeleteItems(id);
  const restoreItems = useRestoreItems(id);
  const renameList = useRenameList(id);
  const setListHousehold = useSetListHousehold(id);
  const deleteList = useDeleteList();
  const { data: households = [] } = useHouseholds();
  const { userId } = useAuth();
  const { toast, confirm } = useFeedback();

  const showError = (fallback: string) => (err: unknown) =>
    toast({ message: getErrorMessage(err, fallback), isError: true });

  if (!list) {
    return (
      <div>
        <BackLink />
        {error ? (
          <p className="form-error">{getErrorMessage(error, "Could not load grocery list")}</p>
        ) : (
          <>
            <div className="loading">Loading…</div>
            <SlowServerNotice active={isPending} />
          </>
        )}
      </div>
    );
  }

  // Ticked items move to the bottom, so the list shrinks as you shop
  const toBuy = list.items.filter((item) => !item.isChecked);
  const checked = list.items.filter((item) => item.isChecked);

  const toggleCheck = (item: Item) => {
    updateItem.mutate(
      { itemId: item.id, data: { isChecked: !item.isChecked } },
      { onError: showError("Could not update the item") }
    );
  };

  const handleSaveItem = (item: Item, changes: ItemChanges) => {
    setEditingItemId(null);
    if (changes.name === item.name && changes.quantity === item.quantity && changes.unit === (item.unit ?? null)) return;
    updateItem.mutate({ itemId: item.id, data: changes }, { onError: showError("Could not update the item") });
  };

  // Deleting is immediate, with Undo in the toast instead of a confirmation
  const undoToast = (message: string, items: Item[]) =>
    toast({
      message,
      actionLabel: "Undo",
      onAction: () => restoreItems.mutate(items, { onError: showError("Could not put the items back") }),
    });

  const handleDeleteItem = (item: Item) => {
    setEditingItemId(null);
    deleteItem.mutate(item.id, { onError: showError("Could not delete the item") });
    undoToast(`Deleted ${item.name}`, [item]);
  };

  const handleClearChecked = () => {
    deleteItems.mutate(
      checked.map((item) => item.id),
      { onError: showError("Could not clear the ticked items") }
    );
    undoToast(`Cleared ${checked.length} ticked ${checked.length === 1 ? "item" : "items"}`, checked);
  };

  const handleSaveName = (e: React.FormEvent) => {
    e.preventDefault();
    if (!editedName.trim()) return;
    renameList.mutate(editedName.trim(), {
      onSuccess: () => setIsEditingName(false),
      onError: showError("Could not rename the list"),
    });
  };

  const handleShareChange = (householdId: string) => {
    setListHousehold.mutate(householdId || null, {
      onError: showError("Could not change who the list is shared with"),
    });
  };

  const handleDeleteList = async () => {
    const confirmed = await confirm({
      title: "Delete list?",
      message: list.householdId
        ? `"${list.name}" and its items will be deleted for everyone in the household.`
        : `"${list.name}" and its items will be deleted.`,
      confirmLabel: "Delete",
      danger: true,
    });
    if (!confirmed) return;
    // Leave first, so this page doesn't refetch the deleted list. mutateAsync, because
    // mutate's callbacks don't run once the page is gone.
    navigate("/lists", { replace: true });
    deleteList
      .mutateAsync(list.id)
      .then(() => toast({ message: `Deleted "${list.name}"` }), showError("Could not delete the list"));
  };

  const renderItem = (item: Item) => (
    <ItemRow
      key={item.id}
      item={item}
      isEditing={editingItemId === item.id}
      onToggle={() => toggleCheck(item)}
      onEdit={() => setEditingItemId(item.id)}
      onSave={(changes) => handleSaveItem(item, changes)}
      onCancel={() => setEditingItemId(null)}
      onDelete={() => handleDeleteItem(item)}
    />
  );

  return (
    <div>
      <BackLink />

      <div className="list-header">
        {isEditingName ? (
          <form className="inline-edit-form" onSubmit={handleSaveName}>
            <input
              type="text"
              className="form-input"
              value={editedName}
              onChange={(e) => setEditedName(e.target.value)}
              aria-label="List name"
              maxLength={100}
              autoFocus
            />
            <button type="submit" className="btn btn-primary btn-small" disabled={renameList.isPending || !editedName.trim()}>
              Save
            </button>
            <button type="button" className="btn btn-secondary btn-small" onClick={() => setIsEditingName(false)}>
              Cancel
            </button>
          </form>
        ) : (
          <div className="title-row">
            <h1>{list.name}</h1>
            <button
              className="icon-btn"
              onClick={() => {
                setEditedName(list.name);
                setIsEditingName(true);
              }}
              aria-label="Rename list"
            >
              <PencilIcon />
            </button>
          </div>
        )}
        {list.items.length > 0 && (
          <p className="muted list-progress">
            {toBuy.length === 0
              ? "All done"
              : checked.length === 0
                ? `${toBuy.length} ${toBuy.length === 1 ? "item" : "items"}`
                : `${toBuy.length} of ${list.items.length} left`}
          </p>
        )}
      </div>

      {list.userId === userId && households.length > 0 ? (
        <div className="list-sharing">
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

      {error && <p className="offline-banner">Can't reach the server. Showing the last saved copy.</p>}

      <AddItemBar list={list} />

      {list.items.length === 0 ? (
        <div className="empty-state">
          <p>Nothing on this list yet. Add the first item above.</p>
        </div>
      ) : (
        <>
          {toBuy.length > 0 ? (
            <ul className="items-list">{toBuy.map(renderItem)}</ul>
          ) : (
            <p className="empty-state">Everything is ticked off.</p>
          )}

          {checked.length > 0 && (
            <section className="checked-section">
              <div className="checked-header">
                <button
                  className="checked-toggle"
                  onClick={() => setShowChecked(!showChecked)}
                  aria-expanded={showChecked}
                >
                  <ChevronIcon open={showChecked} />
                  In the cart ({checked.length})
                </button>
                <button className="btn btn-secondary btn-small" onClick={handleClearChecked}>
                  Clear ticked items
                </button>
              </div>
              {showChecked && <ul className="items-list">{checked.map(renderItem)}</ul>}
            </section>
          )}
        </>
      )}

      <div className="page-footer-actions">
        <button className="btn btn-danger-outline btn-small" onClick={handleDeleteList} disabled={deleteList.isPending}>
          Delete list
        </button>
      </div>
    </div>
  );
}

export default ListDetailsPage;
