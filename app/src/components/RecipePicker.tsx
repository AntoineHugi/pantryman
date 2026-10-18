import { useEffect, useState } from 'react';
import type { RecipeSummary } from '../api/types';
import { useRecipes } from '../hooks/useRecipes';

// Full-screen on phones: search the recipes and pick one
export default function RecipePicker({
  title,
  onPick,
  onClose,
}: {
  title: string;
  onPick: (recipe: RecipeSummary) => void;
  onClose: () => void;
}) {
  const { data: recipes = [], isPending } = useRecipes();
  const [query, setQuery] = useState('');
  const q = query.trim().toLowerCase();
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
        <input
          type="search"
          className="form-input"
          placeholder="Search recipes"
          aria-label="Search recipes"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <ul className="picker-list">
          {isPending && <li className="muted">Loading…</li>}
          {!isPending && recipes.length === 0 && <li className="muted">No recipes yet. Add some in Recipes first.</li>}
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
