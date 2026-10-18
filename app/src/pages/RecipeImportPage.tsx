import { useState } from "react";
import { Link } from "react-router-dom";
import { getErrorMessage } from "../api/client";
import type { RecipeImportResponse } from "../api/types";
import BackLink from "../components/BackLink";
import SlowServerNotice from "../components/SlowServerNotice";
import { useImportRecipes } from "../hooks/useRecipes";
import "../App.css";

// Recipe files are made outside the app (docs/recipe-format.md), e.g. by converting a text
// document with Claude. One recipe, or an array of them.
function RecipeImportPage() {
  const [text, setText] = useState("");
  const [parseError, setParseError] = useState<string | null>(null);
  const [result, setResult] = useState<RecipeImportResponse | null>(null);
  const importRecipes = useImportRecipes();

  const handleFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setText(await file.text());
    setParseError(null);
    setResult(null);
    importRecipes.reset();
  };

  const handleImport = (e: React.FormEvent) => {
    e.preventDefault();
    let parsed: unknown;
    try {
      parsed = JSON.parse(text);
    } catch (err) {
      setParseError(`This isn't valid JSON: ${err instanceof Error ? err.message : "check the file"}`);
      return;
    }
    setParseError(null);
    importRecipes.mutate(parsed, {
      onSuccess: (response) => {
        setResult(response);
        setText("");
      },
    });
  };

  return (
    <div>
      <BackLink to="/recipes" label="Recipes" />
      <h1>Import recipes</h1>
      <p className="page-intro">
        Choose a recipe file (.json), or paste its contents. A file can hold one recipe or several. Recipes you already
        added under the same name are skipped.
      </p>

      {result && (
        <div className="card import-result" role="status">
          <p>
            <strong>
              Imported {result.imported.length} {result.imported.length === 1 ? "recipe" : "recipes"}.
            </strong>
          </p>
          {result.imported.length > 0 && (
            <ul>
              {result.imported.map((recipe) => (
                <li key={recipe.id}>
                  <Link to={`/recipes/${recipe.id}`}>{recipe.name}</Link>
                </li>
              ))}
            </ul>
          )}
          {result.skipped.length > 0 && (
            <p className="muted">Skipped, already added: {result.skipped.join(", ")}</p>
          )}
        </div>
      )}

      <form onSubmit={handleImport}>
        <div className="form-group">
          <label className="form-label" htmlFor="import-file">Recipe file</label>
          <input id="import-file" className="file-input" type="file" accept=".json,application/json" onChange={handleFile} />
        </div>
        <div className="form-group">
          <label className="form-label" htmlFor="import-text">Or paste the JSON</label>
          <textarea
            id="import-text"
            className="form-input import-text"
            rows={10}
            value={text}
            onChange={(e) => setText(e.target.value)}
            spellCheck={false}
            placeholder={'{ "formatVersion": 1, "name": "…", … }'}
          />
        </div>
        {parseError && <p className="form-error">{parseError}</p>}
        {importRecipes.isError && (
          <p className="form-error">{getErrorMessage(importRecipes.error, "Could not import the recipes")}</p>
        )}
        <SlowServerNotice active={importRecipes.isPending} />
        <div className="form-actions">
          <button type="submit" className="btn btn-primary" disabled={!text.trim() || importRecipes.isPending}>
            Import
          </button>
        </div>
      </form>
    </div>
  );
}

export default RecipeImportPage;
