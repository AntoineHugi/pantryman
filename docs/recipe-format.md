# Recipe import format

Recipes are imported into Pantryman as JSON. A file holds one recipe (an object) or several (an array of objects). Everything is in English.

Import a file in the app: Recipes → Import (pick the file or paste its contents). The whole file is checked first: if one recipe is invalid, nothing is imported and the error names the recipe and field. Recipes you already added under the same name are skipped, so importing a file again is harmless.

This document is also the instruction sheet for converting a recipe from a text document (by hand, or with a Claude skill): follow the rules below, then check the result against the example.

## Fields

| Field | Type | Required | Notes |
|---|---|---|---|
| `formatVersion` | number | yes | Always `1`. |
| `name` | string | yes | Title case, e.g. `"Chicken Curry"`. At most 200 characters. |
| `servings` | number | yes | Whole number, 1–100. If the source doesn't say, estimate and mention it in `notes`. |
| `description` | string | no | One or two sentences. |
| `prepMinutes` | number | no | Whole minutes. |
| `cookMinutes` | number | no | Whole minutes. |
| `tags` | string[] | no | Lowercase, e.g. `["vegetarian", "pasta", "quick"]`. |
| `sourceUrl` | string | no | Only if the source is a web page. |
| `ingredients` | object[] | yes | At least one. See below. |
| `steps` | string[] | no | One instruction per entry, in order, without numbering. |
| `notes` | string | no | Tips, variations, storage. |

### Ingredients

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | string | yes | The ingredient only: **lowercase, singular, generic**. `"onion"`, not `"2 large onions, finely chopped"`. |
| `amount` | number or null | yes | Decimal allowed (`0.5`, not `"1/2"`). `null` when there is no amount ("salt to taste"). |
| `unit` | string or null | yes | One of the units below, or `null` for a count ("2 eggs" → amount 2, unit null). |
| `note` | string | no | Preparation or detail: `"finely chopped"`, `"at room temperature"`, `"or half pork"`. |
| `section` | string | no | Group heading when the recipe has several, e.g. `"Sauce"`, `"Topping"`. |
| `optional` | boolean | no | `true` for optional ingredients. Default `false`. |

**Units:** `g`, `kg`, `ml`, `l`, `tsp`, `tbsp`, `cup`, `pinch`, `clove`, `can`, `bunch`, `slice`, `pack`.

## Conversion rules

- Translate to English (ingredient names, steps, units). German `EL` → `tbsp`, `TL` → `tsp`, `Prise` → `pinch`, `Bund` → `bunch`, `Dose` → `can`, `Päckchen` → `pack`.
- Keep the source's quantities. Don't convert cups to grams or scale the recipe.
- Ranges ("2–3 onions") use the higher value, with the range in `note`: `"2–3"`.
- Fractions become decimals: `1/2` → `0.5`, `1 1/2` → `1.5`, `1/3` → `0.33`.
- Use the same name for the same ingredient across recipes (`"olive oil"`, `"garlic"`, `"onion"`). The grocery list merges ingredients by name and unit, so `"onions"` and `"onion"` would end up as two lines.
- Garlic: `{"amount": 2, "unit": "clove", "name": "garlic"}`.
- "Salt and pepper" becomes two ingredients, each with `amount: null` and `unit: null`.
- Don't invent anything that isn't in the source, except `servings` (estimated if missing, noted in `notes`).
- Output valid JSON only: no comments, no trailing commas.

## Example

```json
{
  "formatVersion": 1,
  "name": "Spaghetti Bolognese",
  "servings": 4,
  "description": "A family classic, best made a day ahead.",
  "prepMinutes": 15,
  "cookMinutes": 90,
  "tags": ["pasta", "beef"],
  "ingredients": [
    { "amount": 500, "unit": "g", "name": "minced beef", "note": "or half pork" },
    { "amount": 1, "unit": null, "name": "onion", "note": "finely chopped" },
    { "amount": 2, "unit": "clove", "name": "garlic" },
    { "amount": 1, "unit": null, "name": "carrot", "note": "grated" },
    { "amount": 2, "unit": "tbsp", "name": "olive oil" },
    { "amount": 400, "unit": "g", "name": "chopped tomatoes", "note": "1 can" },
    { "amount": 2, "unit": "tbsp", "name": "tomato paste" },
    { "amount": 150, "unit": "ml", "name": "red wine", "optional": true },
    { "amount": 500, "unit": "g", "name": "spaghetti" },
    { "amount": null, "unit": null, "name": "salt" },
    { "amount": null, "unit": null, "name": "black pepper" },
    { "amount": 50, "unit": "g", "name": "parmesan", "section": "To serve" }
  ],
  "steps": [
    "Heat the olive oil and soften the onion, carrot and garlic for 5 minutes.",
    "Add the beef and brown it, breaking it up.",
    "Add the wine, if using, and let it reduce by half.",
    "Stir in the tomato paste and chopped tomatoes, then simmer for at least an hour.",
    "Season with salt and pepper. Cook the spaghetti and serve with grated parmesan."
  ],
  "notes": "Freezes well for up to 3 months."
}
```
