import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { getErrorMessage } from "../api/client";
import type { Recipe, RecipeInput } from "../api/types";
import BackLink from "../components/BackLink";
import SlowServerNotice from "../components/SlowServerNotice";
import { useAuth } from "../hooks/useAuth";
import { useCreateRecipe, useRecipe, useUpdateRecipe } from "../hooks/useRecipes";
import { formatIngredientsText, parseIngredientsText } from "../utils/recipeText";
import "../App.css";

const INGREDIENTS_PLACEHOLDER = `500 g minced beef, or half pork
2 cloves garlic
2 eggs
salt

Sauce:
400 g chopped tomatoes
1 tbsp olive oil (optional)`;

// "" → null; otherwise a whole number of minutes
function parseMinutes(value: string): number | null | undefined {
  if (!value.trim()) return null;
  return /^\d+$/.test(value.trim()) ? Number(value) : undefined;
}

function RecipeForm({ recipe }: { recipe?: Recipe }) {
  const navigate = useNavigate();
  const createRecipe = useCreateRecipe();
  const updateRecipe = useUpdateRecipe();
  const mutation = recipe ? updateRecipe : createRecipe;

  const [name, setName] = useState(recipe?.name ?? "");
  const [servings, setServings] = useState(String(recipe?.servings ?? 4));
  const [prepMinutes, setPrepMinutes] = useState(recipe?.prepMinutes != null ? String(recipe.prepMinutes) : "");
  const [cookMinutes, setCookMinutes] = useState(recipe?.cookMinutes != null ? String(recipe.cookMinutes) : "");
  const [description, setDescription] = useState(recipe?.description ?? "");
  const [tags, setTags] = useState(recipe?.tags.join(", ") ?? "");
  const [ingredients, setIngredients] = useState(recipe ? formatIngredientsText(recipe.ingredients) : "");
  const [steps, setSteps] = useState(recipe?.steps.join("\n") ?? "");
  const [notes, setNotes] = useState(recipe?.notes ?? "");
  const [sourceUrl, setSourceUrl] = useState(recipe?.sourceUrl ?? "");
  const [formError, setFormError] = useState<string | null>(null);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const servingsNumber = Number(servings);
    const prep = parseMinutes(prepMinutes);
    const cook = parseMinutes(cookMinutes);
    const parsedIngredients = parseIngredientsText(ingredients);

    if (!name.trim()) return setFormError("Give the recipe a name.");
    if (!Number.isInteger(servingsNumber) || servingsNumber < 1 || servingsNumber > 100) {
      return setFormError("People must be a whole number from 1 to 100.");
    }
    if (prep === undefined || cook === undefined) return setFormError("Times must be whole minutes.");
    if (parsedIngredients.length === 0) return setFormError("Add at least one ingredient.");
    setFormError(null);

    const data: RecipeInput = {
      name: name.trim(),
      servings: servingsNumber,
      prepMinutes: prep,
      cookMinutes: cook,
      description: description.trim() || null,
      tags: tags.split(",").map((t) => t.trim()).filter(Boolean),
      ingredients: parsedIngredients,
      steps: steps.split("\n").map((s) => s.trim()).filter(Boolean),
      notes: notes.trim() || null,
      sourceUrl: sourceUrl.trim() || null,
    };
    const onSuccess = (saved: Recipe) => navigate(`/recipes/${saved.id}`, { replace: true });
    if (recipe) {
      updateRecipe.mutate({ recipeId: recipe.id, data }, { onSuccess });
    } else {
      createRecipe.mutate(data, { onSuccess });
    }
  };

  return (
    <form className="recipe-form" onSubmit={handleSubmit} noValidate>
      <div className="form-group">
        <label className="form-label" htmlFor="recipe-name">Name</label>
        <input id="recipe-name" className="form-input" value={name} onChange={(e) => setName(e.target.value)} maxLength={200} />
      </div>

      <div className="form-row">
        <div className="form-group">
          <label className="form-label" htmlFor="recipe-servings">Feeds (people)</label>
          <input id="recipe-servings" className="form-input" inputMode="numeric" value={servings} onChange={(e) => setServings(e.target.value)} />
        </div>
        <div className="form-group">
          <label className="form-label" htmlFor="recipe-prep">Prep (min)</label>
          <input id="recipe-prep" className="form-input" inputMode="numeric" value={prepMinutes} onChange={(e) => setPrepMinutes(e.target.value)} />
        </div>
        <div className="form-group">
          <label className="form-label" htmlFor="recipe-cook">Cook (min)</label>
          <input id="recipe-cook" className="form-input" inputMode="numeric" value={cookMinutes} onChange={(e) => setCookMinutes(e.target.value)} />
        </div>
      </div>

      <div className="form-group">
        <label className="form-label" htmlFor="recipe-ingredients">Ingredients</label>
        <p className="form-hint" id="recipe-ingredients-hint">
          One per line: amount, unit, name, then a comma and any detail. A line ending with “:” starts a section.
        </p>
        <textarea
          id="recipe-ingredients"
          className="form-input"
          rows={10}
          value={ingredients}
          onChange={(e) => setIngredients(e.target.value)}
          placeholder={INGREDIENTS_PLACEHOLDER}
          aria-describedby="recipe-ingredients-hint"
        />
      </div>

      <div className="form-group">
        <label className="form-label" htmlFor="recipe-steps">Steps</label>
        <p className="form-hint" id="recipe-steps-hint">One step per line.</p>
        <textarea
          id="recipe-steps"
          className="form-input"
          rows={8}
          value={steps}
          onChange={(e) => setSteps(e.target.value)}
          aria-describedby="recipe-steps-hint"
        />
      </div>

      <div className="form-group">
        <label className="form-label" htmlFor="recipe-description">Description</label>
        <textarea id="recipe-description" className="form-input" rows={2} value={description} onChange={(e) => setDescription(e.target.value)} maxLength={1000} />
      </div>

      <div className="form-group">
        <label className="form-label" htmlFor="recipe-tags">Tags</label>
        <input id="recipe-tags" className="form-input" placeholder="vegetarian, pasta, quick" value={tags} onChange={(e) => setTags(e.target.value)} />
      </div>

      <div className="form-group">
        <label className="form-label" htmlFor="recipe-notes">Notes</label>
        <textarea id="recipe-notes" className="form-input" rows={3} value={notes} onChange={(e) => setNotes(e.target.value)} />
      </div>

      <div className="form-group">
        <label className="form-label" htmlFor="recipe-source">Source link</label>
        <input id="recipe-source" className="form-input" type="url" inputMode="url" placeholder="https://" value={sourceUrl} onChange={(e) => setSourceUrl(e.target.value)} />
      </div>

      {formError && <p className="form-error">{formError}</p>}
      {mutation.isError && <p className="form-error">{getErrorMessage(mutation.error, "Could not save the recipe")}</p>}
      <SlowServerNotice active={mutation.isPending} />
      <div className="form-actions">
        <button type="submit" className="btn btn-primary" disabled={mutation.isPending}>
          {recipe ? "Save" : "Add recipe"}
        </button>
      </div>
    </form>
  );
}

export function NewRecipePage() {
  return (
    <div>
      <BackLink to="/recipes" label="Recipes" />
      <h1>New recipe</h1>
      <p className="page-intro">Recipes are shared with everyone on Pantryman. Only you can edit yours.</p>
      <RecipeForm />
    </div>
  );
}

export function EditRecipePage() {
  const { id = "" } = useParams();
  const { userId } = useAuth();
  const { data: recipe, isPending, error } = useRecipe(id);

  return (
    <div>
      <BackLink to={`/recipes/${id}`} label="Recipe" />
      <h1>Edit recipe</h1>
      {isPending && (
        <>
          <div className="loading">Loading…</div>
          <SlowServerNotice active />
        </>
      )}
      {!isPending && !recipe && <p className="form-error">{getErrorMessage(error, "Could not load the recipe")}</p>}
      {recipe && recipe.createdBy !== userId && (
        <p className="form-error">Only the person who added this recipe can edit it.</p>
      )}
      {/* Keyed so the form starts from the loaded recipe */}
      {recipe && recipe.createdBy === userId && <RecipeForm key={recipe.id} recipe={recipe} />}
    </div>
  );
}
