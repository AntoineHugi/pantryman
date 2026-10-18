import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { getErrorMessage } from '../api/client';
import type { Recipe } from '../api/types';
import { useFeedback } from '../hooks/useFeedback';
import { useAddRecipeToList, useLists } from '../hooks/useGroceryLists';
import { formatQuantity } from '../utils/recipeText';
import SlowServerNotice from './SlowServerNotice';

const LAST_LIST_KEY = 'pantryman-last-recipe-list';

function rememberedListId(): string | null {
  try {
    return localStorage.getItem(LAST_LIST_KEY);
  } catch {
    return null;
  }
}

function rememberListId(listId: string) {
  try {
    localStorage.setItem(LAST_LIST_KEY, listId);
  } catch {
    // Storage unavailable: the first list is picked next time
  }
}

// On a recipe page: tick the ingredients to buy (optional ones and anything already at home
// unticked), pick a list, and they're added, scaled to the servings shown and merged with
// what's already on the list.
export default function AddRecipeToList({
  recipe,
  servings,
  onDone,
}: {
  recipe: Recipe;
  servings: number;
  onDone: () => void;
}) {
  const navigate = useNavigate();
  const { toast } = useFeedback();
  const { data: lists = [], isPending: listsPending } = useLists();
  const addRecipe = useAddRecipeToList();
  const [selected, setSelected] = useState(
    () => new Set(recipe.ingredients.flatMap((ingredient, index) => (ingredient.optional ? [] : [index])))
  );
  const [chosenListId, setChosenListId] = useState<string | null>(null);
  const remembered = rememberedListId();
  const listId =
    chosenListId ?? (lists.some((l) => l.id === remembered) ? remembered : null) ?? lists[0]?.id ?? '';
  const scale = servings / recipe.servings;

  const toggle = (index: number) => {
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(index)) next.delete(index);
      else next.add(index);
      return next;
    });
  };

  const handleAdd = () => {
    const list = lists.find((l) => l.id === listId);
    if (!list || selected.size === 0) return;
    rememberListId(list.id);
    addRecipe.mutate(
      { listId: list.id, data: { recipeId: recipe.id, servings, ingredientIndexes: [...selected].sort((a, b) => a - b) } },
      {
        onSuccess: ({ added, merged }) => {
          const parts = [
            added > 0 ? `${added} new` : null,
            merged > 0 ? `${merged} already on the list` : null,
          ].filter(Boolean);
          toast({
            message: `Added to ${list.name}${parts.length ? ` (${parts.join(', ')})` : ''}`,
            actionLabel: 'Open',
            onAction: () => navigate(`/lists/${list.id}/items`),
          });
          onDone();
        },
      }
    );
  };

  if (!listsPending && lists.length === 0) {
    return (
      <div className="add-to-list">
        <p>
          You have no grocery lists yet. <Link to="/lists">Create a list</Link> first.
        </p>
        <button className="btn btn-secondary btn-small" onClick={onDone}>
          Cancel
        </button>
      </div>
    );
  }

  return (
    <div className="add-to-list">
      <p className="form-hint">Untick what you already have.</p>
      <ul className="ingredient-checklist">
        {recipe.ingredients.map((ingredient, index) => (
          <li key={index}>
            <label>
              <input type="checkbox" checked={selected.has(index)} onChange={() => toggle(index)} />
              <span className="ingredient-quantity">{formatQuantity(ingredient, scale)}</span>
              <span>
                {ingredient.name}
                {ingredient.optional && <span className="muted"> (optional)</span>}
              </span>
            </label>
          </li>
        ))}
      </ul>
      <div className="form-group">
        <label className="form-label" htmlFor="add-to-list-select">
          Add to
        </label>
        <select
          id="add-to-list-select"
          className="form-input"
          value={listId}
          onChange={(e) => setChosenListId(e.target.value)}
          disabled={listsPending}
        >
          {lists.map((l) => (
            <option key={l.id} value={l.id}>
              {l.name}
            </option>
          ))}
        </select>
      </div>
      {addRecipe.isError && <p className="form-error">{getErrorMessage(addRecipe.error, 'Could not add to the list')}</p>}
      <SlowServerNotice active={addRecipe.isPending || listsPending} />
      <div className="form-actions">
        <button
          className="btn btn-primary"
          onClick={handleAdd}
          disabled={addRecipe.isPending || selected.size === 0 || !listId}
        >
          Add {selected.size} {selected.size === 1 ? 'item' : 'items'}
        </button>
        <button className="btn btn-secondary" onClick={onDone}>
          Cancel
        </button>
      </div>
    </div>
  );
}
