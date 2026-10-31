import { useEffect, useState } from 'react';
import type { RecipeSummary } from '../api/types';
import { useRecipes } from '../hooks/useRecipes';

// Full-screen on phones: search the recipes and pick one, or use what was typed as free text
export default function RecipePicker({
  title,
  initialText = '',
  onPick,
  onText,
  onClose,
}: {
  title: string;
  initialText?: string;
  onPick: (recipe: RecipeSummary) => void;
  onText: (text: string) => void;
  onClose: () => void;
}) {
  const { data: recipes = [], isPending } = useRecipes();
  const [query, setQuery] = useState(initialText);
  const text = query.trim();
  const q = text.toLowerCase();
  // Typing a recipe's exact name means that recipe, not free text
  const exact = recipes.find((r) => r.name.toLowerCase() === q);
  const shown = recipes.filter((r) => !q || [r.name, ...r.tags].some((t) => t.toLowerCase().includes(q)));

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div className="dialog-backdrop" onClick={onClose}>
      <div className="dialog picker" role="dialog" aria-modal="true" aria-label={title} onClick={(e) => e.stopPropagation()}>
        <div className="picker-header">
          <h2 className="dialog-title">{title}</h2>
          <button className="btn btn-secondary btn-small" onClick={onClose}>
            Cancel
          </button>
        </div>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (exact) onPick(exact);
            else if (text) onText(text);
          }}
        >
          <input
            type="search"
            className="form-input"
            placeholder="Search recipes or type a meal"
            aria-label="Search recipes or type a meal"
            maxLength={200}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </form>
        <ul className="picker-list">
          {text && !exact && (
            <li>
              <button className="picker-item" onClick={() => onText(text)}>
                <span>Use “{text}”</span>
                <span className="recipe-card-meta">Without a recipe, not added to the grocery list</span>
              </button>
            </li>
          )}
          {isPending && <li className="muted">Loading…</li>}
          {!isPending && recipes.length === 0 && !text && <li className="muted">No recipes yet. Type a meal, or add recipes in Recipes.</li>}
          {!isPending && recipes.length > 0 && shown.length === 0 && <li className="muted">No recipe matches.</li>}
          {shown.map((recipe) => (
            <li key={recipe.id}>
              <button className="picker-item" onClick={() => onPick(recipe)}>
                <span>{recipe.name}</span>
                {recipe.tags.length > 0 && <span className="recipe-card-meta">{recipe.tags.join(', ')}</span>}
              </button>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
