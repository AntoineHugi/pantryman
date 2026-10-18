import { useState } from "react";
import { getErrorMessage } from "../api/client";
import type { PantryItem, PantryKind } from "../api/types";
import BackLink from "../components/BackLink";
import SlowServerNotice from "../components/SlowServerNotice";
import { useFeedback } from "../hooks/useFeedback";
import { useHouseholds } from "../hooks/useHouseholds";
import { useAddPantryItem, useDeletePantryItem, usePantry } from "../hooks/usePantry";
import { formatItemQuantity, parseItemInput } from "../utils/itemInput";
import "../App.css";

const SECTIONS: { kind: PantryKind; title: string; hint: string; placeholder: string; moveLabel: string }[] = [
  {
    kind: "default",
    title: "Always buy",
    hint: "Added to every new list. You can leave any of them out when you create the list.",
    placeholder: "e.g. 2 milk, 500 g yogurt",
    moveLabel: "At home",
  },
  {
    kind: "staple",
    title: "Always at home",
    hint: "Recipes and meal plans skip these. Tick them on a recipe when you run out.",
    placeholder: "e.g. salt, olive oil",
    moveLabel: "Buy",
  },
];

function PantrySection({ kind, householdId, items }: { kind: PantryKind; householdId: string | null; items: PantryItem[] }) {
  const section = SECTIONS.find((s) => s.kind === kind)!;
  const [text, setText] = useState("");
  const addItem = useAddPantryItem();
  const deleteItem = useDeletePantryItem();
  const { toast } = useFeedback();
  const showError = (fallback: string) => (err: unknown) => toast({ message: getErrorMessage(err, fallback), isError: true });

  const handleAdd = (e: React.FormEvent) => {
    e.preventDefault();
    // Staples have no quantity: "2 salt" would just be "salt"
    const parsed = kind === "default" ? parseItemInput(text) : { name: text.trim(), quantity: 1, unit: null };
    if (!parsed.name) return;
    addItem.mutate(
      { householdId, name: parsed.name, kind, quantity: parsed.quantity, unit: parsed.unit },
      { onSuccess: () => setText(""), onError: showError("Could not add the item") }
    );
  };

  const move = (item: PantryItem) => {
    const other: PantryKind = kind === "default" ? "staple" : "default";
    addItem.mutate(
      { householdId, name: item.name, kind: other, quantity: item.quantity, unit: item.unit },
      { onError: showError("Could not move the item") }
    );
  };

  return (
    <section className="card pantry-section">
      <h2>{section.title}</h2>
      <p className="form-hint">{section.hint}</p>
      <form className="add-item-form" onSubmit={handleAdd}>
        <input
          className="form-input"
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder={section.placeholder}
          aria-label={`Add to ${section.title}`}
          maxLength={100}
          enterKeyHint="done"
          autoComplete="off"
        />
        <button type="submit" className="btn btn-primary" disabled={!text.trim() || addItem.isPending}>
          Add
        </button>
      </form>
      {items.length > 0 && (
        <ul className="pantry-list">
          {items.map((item) => (
            <li key={item.id}>
              <span className="pantry-name">{item.name}</span>
              {kind === "default" && <span className="item-quantity">{formatItemQuantity(item)}</span>}
              <button className="btn btn-secondary btn-small" onClick={() => move(item)} aria-label={`Move ${item.name} to ${section.moveLabel}`}>
                → {section.moveLabel}
              </button>
              <button
                className="icon-btn"
                onClick={() => deleteItem.mutate(item, { onError: showError("Could not remove the item") })}
                aria-label={`Remove ${item.name}`}
              >
                ✕
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

// The household's (or the user's own) usual items: what every list starts with, and what's always at home
function PantryPage() {
  const { data: households = [] } = useHouseholds();
  // "" = personal; undefined = not chosen yet, which defaults to the first household
  const [chosen, setChosen] = useState<string | undefined>(undefined);
  const scope = chosen ?? households[0]?.id ?? "";
  const householdId = scope || null;
  const { data: items = [], isPending, error } = usePantry(householdId);

  return (
    <div>
      <BackLink />
      <h1>Usual items</h1>
      <p className="page-intro">
        {households.length > 0
          ? "Each household has its own, used by its shared lists. Your personal lists use yours."
          : "Used by your lists and recipes."}
      </p>
      {households.length > 0 && (
        <div className="form-group">
          <select className="form-input" value={scope} onChange={(e) => setChosen(e.target.value)} aria-label="Whose items">
            {households.map((h) => (
              <option key={h.id} value={h.id}>{h.name}</option>
            ))}
            <option value="">Personal lists</option>
          </select>
        </div>
      )}
      {error && <p className="form-error">{getErrorMessage(error, "Could not load the items")}</p>}
      {isPending && (
        <>
          <div className="loading">Loading…</div>
          <SlowServerNotice active />
        </>
      )}
      {!isPending &&
        SECTIONS.map((s) => (
          <PantrySection key={`${scope}-${s.kind}`} kind={s.kind} householdId={householdId} items={items.filter((i) => i.kind === s.kind)} />
        ))}
    </div>
  );
}

export default PantryPage;
