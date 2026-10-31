import { useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { getErrorMessage } from "../api/client";
import BackLink from "../components/BackLink";
import SlowServerNotice from "../components/SlowServerNotice";
import { useAuth } from "../hooks/useAuth";
import { useFeedback } from "../hooks/useFeedback";
import { useDeleteRecipe, useRecipe } from "../hooks/useRecipes";
import AddRecipeToList from "../components/AddRecipeToList";
import { formatMinutes, formatPeople, formatQuantity, groupBySection } from "../utils/recipeText";
import "../App.css";

const MAX_SERVINGS = 100;

function RecipePage() {
  const { id = "" } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { userId } = useAuth();
  const { toast, confirm } = useFeedback();
  const { data: recipe, isPending, error } = useRecipe(id);
  const deleteRecipe = useDeleteRecipe();
  // null = the recipe's own servings. A meal plan links here with its meal's ?servings=
  const [servings, setServings] = useState<number | null>(() => {
    const planned = Number(searchParams.get("servings"));
    return Number.isInteger(planned) && planned >= 1 && planned <= MAX_SERVINGS ? planned : null;
  });
  // Choosing which ingredients go on a grocery list
  const [isAdding, setIsAdding] = useState(false);

  if (isPending) {
    return (
      <div>
        <BackLink to="/recipes" label="Recipes" />
        <div className="loading">Loading…</div>
        <SlowServerNotice active />
      </div>
    );
  }
  if (!recipe) {
    return (
      <div>
        <BackLink to="/recipes" label="Recipes" />
        <p className="form-error">{getErrorMessage(error, "Could not load the recipe")}</p>
      </div>
    );
  }

  const isOwner = recipe.createdBy === userId;
  const shownServings = servings ?? recipe.servings;
  const scale = shownServings / recipe.servings;

  const handleDelete = async () => {
    const confirmed = await confirm({
      title: "Delete recipe?",
      message: `Delete "${recipe.name}" for everyone? This can't be undone.`,
      confirmLabel: "Delete",
      danger: true,
    });
    if (!confirmed) return;
    deleteRecipe.mutate(recipe.id, {
      onSuccess: () => {
        toast({ message: `Deleted "${recipe.name}"` });
        navigate("/recipes", { replace: true });
      },
      onError: (err) => toast({ message: getErrorMessage(err, "Could not delete the recipe"), isError: true }),
    });
  };

  const times = [
    recipe.prepMinutes ? `Prep ${formatMinutes(recipe.prepMinutes)}` : null,
    recipe.cookMinutes ? `Cook ${formatMinutes(recipe.cookMinutes)}` : null,
  ].filter(Boolean);

  return (
    <div className="recipe-page">
      <BackLink to="/recipes" label="Recipes" />
      <h1>{recipe.name}</h1>
      {(times.length > 0 || recipe.tags.length > 0) && (
        <p className="recipe-meta">
          {times.join(" · ")}
          {times.length > 0 && recipe.tags.length > 0 && " · "}
          {recipe.tags.join(", ")}
        </p>
      )}
      {recipe.description && <p className="recipe-description">{recipe.description}</p>}

      <section className="card recipe-section">
        <div className="recipe-section-header">
          <h2>Ingredients</h2>
          <div className="stepper" aria-label="People">
            <button
              type="button"
              onClick={() => setServings(shownServings - 1)}
              disabled={shownServings <= 1}
              aria-label="Fewer people"
            >
              −
            </button>
            <span className="servings-count" aria-live="polite">
              {formatPeople(shownServings)}
            </span>
            <button
              type="button"
              onClick={() => setServings(shownServings + 1)}
              disabled={shownServings >= MAX_SERVINGS}
              aria-label="More people"
            >
              +
            </button>
          </div>
        </div>
        {shownServings !== recipe.servings && (
          <p className="form-hint">
            Amounts adjusted. The recipe feeds {formatPeople(recipe.servings)}.{" "}
            <button type="button" className="link-button" onClick={() => setServings(null)}>Reset</button>
          </p>
        )}
        {isAdding ? (
          <AddRecipeToList recipe={recipe} servings={shownServings} onDone={() => setIsAdding(false)} />
        ) : (
          <>
            {groupBySection(recipe.ingredients).map(({ section, ingredients }) => (
              <div key={section ?? ""}>
                {section && <h3 className="recipe-subheading">{section}</h3>}
                <ul className="ingredient-list">
                  {ingredients.map((ingredient, index) => (
                    <li key={index}>
                      <span className="ingredient-quantity">{formatQuantity(ingredient, scale)}</span>
                      <span>
                        {ingredient.name}
                        {ingredient.note && <span className="muted">, {ingredient.note}</span>}
                        {ingredient.optional && <span className="muted"> (optional)</span>}
                      </span>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
            <button className="btn btn-primary btn-block add-to-list-button" onClick={() => setIsAdding(true)}>
              Add to grocery list
            </button>
          </>
        )}
      </section>

      {recipe.steps.length > 0 && (
        <section className="card recipe-section">
          <h2>Steps</h2>
          <ol className="step-list">
            {recipe.steps.map((step, index) => (
              <li key={index}>{step}</li>
            ))}
          </ol>
        </section>
      )}

      {recipe.notes && (
        <section className="card recipe-section">
          <h2>Notes</h2>
          <p className="recipe-notes">{recipe.notes}</p>
        </section>
      )}

      {recipe.sourceUrl && (
        <p>
          <a href={recipe.sourceUrl} target="_blank" rel="noopener noreferrer">
            Original recipe
          </a>
        </p>
      )}

      {isOwner && (
        <div className="form-actions recipe-owner-actions">
          <Link className="btn btn-secondary" to={`/recipes/${recipe.id}/edit`}>
            Edit
          </Link>
          <button className="btn btn-danger-outline" onClick={handleDelete} disabled={deleteRecipe.isPending}>
            Delete
          </button>
        </div>
      )}
    </div>
  );
}

export default RecipePage;
