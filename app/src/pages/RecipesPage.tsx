import { useState } from "react";
import { Link } from "react-router-dom";
import { getErrorMessage } from "../api/client";
import type { RecipeSummary } from "../api/types";
import SlowServerNotice from "../components/SlowServerNotice";
import { useRecipes } from "../hooks/useRecipes";
import { formatMinutes } from "../utils/recipeText";
import "../App.css";

function recipeMeta(recipe: RecipeSummary) {
  const minutes = (recipe.prepMinutes ?? 0) + (recipe.cookMinutes ?? 0);
  return [
    `${recipe.servings} ${recipe.servings === 1 ? "serving" : "servings"}`,
    minutes > 0 ? formatMinutes(minutes) : null,
    recipe.tags.length > 0 ? recipe.tags.join(", ") : null,
  ]
    .filter(Boolean)
    .join(" · ");
}

function matches(recipe: RecipeSummary, query: string) {
  const q = query.trim().toLowerCase();
  if (!q) return true;
  return [recipe.name, recipe.description ?? "", ...recipe.tags].some((text) => text.toLowerCase().includes(q));
}

function RecipesPage() {
  const [query, setQuery] = useState("");
  const { data: recipes = [], isPending, error } = useRecipes();
  const shown = recipes.filter((recipe) => matches(recipe, query));

  return (
    <div>
      <div className="page-header recipes-header">
        <h1>Recipes</h1>
        <div className="header-actions">
          <Link className="btn btn-secondary btn-small" to="/recipes/import">
            Import
          </Link>
          <Link className="btn btn-primary btn-small" to="/recipes/new">
            + New
          </Link>
        </div>
      </div>

      {error && recipes.length === 0 && <p className="form-error">{getErrorMessage(error, "Could not load recipes")}</p>}
      {error && recipes.length > 0 && (
        <p className="offline-banner">Can't reach the server. Showing the last saved copy.</p>
      )}

      {isPending && (
        <>
          <div className="loading">Loading…</div>
          <SlowServerNotice active />
        </>
      )}

      {!isPending && recipes.length === 0 && !error && (
        <div className="empty-state">
          <p>No recipes yet. Recipes are shared with everyone on Pantryman.</p>
          <Link className="btn btn-primary" to="/recipes/new">
            Add the first recipe
          </Link>
        </div>
      )}

      {recipes.length > 0 && (
        <div className="form-group">
          <input
            type="search"
            className="form-input"
            placeholder="Search by name or tag"
            aria-label="Search recipes"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
      )}

      {recipes.length > 0 && shown.length === 0 && <p className="muted">No recipe matches “{query.trim()}”.</p>}

      <ul className="card-list">
        {shown.map((recipe) => (
          <li key={recipe.id}>
            <Link className="list-card recipe-card" to={`/recipes/${recipe.id}`}>
              <span className="list-card-name">{recipe.name}</span>
              <span className="recipe-card-meta">{recipeMeta(recipe)}</span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}

export default RecipesPage;
