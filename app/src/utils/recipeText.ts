import type { RecipeIngredient } from '../api/types';

// Keep in step with docs/recipe-format.md and RecipeService.UNITS on the backend
export const UNITS = ['g', 'kg', 'ml', 'l', 'tsp', 'tbsp', 'cup', 'pinch', 'clove', 'can', 'bunch', 'slice', 'pack'];

// What people type → the unit stored. Plurals and full names of the units above.
const UNIT_ALIASES: Record<string, string> = {
  gram: 'g', grams: 'g', gr: 'g',
  kilo: 'kg', kilos: 'kg', kilogram: 'kg', kilograms: 'kg',
  milliliter: 'ml', milliliters: 'ml', millilitre: 'ml', millilitres: 'ml',
  liter: 'l', liters: 'l', litre: 'l', litres: 'l',
  teaspoon: 'tsp', teaspoons: 'tsp',
  tablespoon: 'tbsp', tablespoons: 'tbsp', tbs: 'tbsp',
  cups: 'cup',
  pinches: 'pinch',
  cloves: 'clove',
  cans: 'can', tin: 'can', tins: 'can',
  bunches: 'bunch',
  slices: 'slice',
  packs: 'pack', packet: 'pack', packets: 'pack',
};

const FRACTION_CHARS: Record<string, number> = { '¼': 0.25, '½': 0.5, '¾': 0.75, '⅓': 1 / 3, '⅔': 2 / 3 };

function toUnit(word: string): string | null {
  const lower = word.toLowerCase();
  if (UNITS.includes(lower)) return lower;
  return UNIT_ALIASES[lower] ?? null;
}

// "2", "1.5", "0,5", "1/2", "1 1/2", "½", "1½" → a number. Null if the text isn't an amount.
function parseAmount(text: string): number | null {
  const t = text.trim().replace(',', '.');
  let m = t.match(/^(\d+)?\s*([¼½¾⅓⅔])$/);
  if (m) return (m[1] ? Number(m[1]) : 0) + FRACTION_CHARS[m[2]];
  m = t.match(/^(?:(\d+)\s+)?(\d+)\/(\d+)$/);
  if (m) return Number(m[3]) === 0 ? null : (m[1] ? Number(m[1]) : 0) + Number(m[2]) / Number(m[3]);
  if (/^\d+(\.\d+)?$/.test(t)) return Number(t);
  return null;
}

const AMOUNT = String.raw`(\d+\s+\d+/\d+|\d+/\d+|\d*[¼½¾⅓⅔]|\d+(?:[.,]\d+)?)`;

/**
 * The amount and unit at the start of "200 g flour", "200g flour", "2 eggs", "a pinch of salt",
 * "1 1/2 cups of milk". Null when the text doesn't start with an amount.
 */
export function parseQuantityPrefix(text: string): { amount: number; unit: string | null; rest: string } | null {
  // "200g flour": a unit attached to the amount
  const attached = text.match(new RegExp(`^${AMOUNT}([a-zA-Z]+)\\s+(.+)$`));
  const spaced = text.match(new RegExp(`^${AMOUNT}\\s+(.+)$`));
  const article = text.match(/^an?\s+(\S+)\s+(.+)$/i);
  let result: { amount: number | null; unit: string | null; rest: string } | null = null;
  if (attached && toUnit(attached[2])) {
    result = { amount: parseAmount(attached[1]), unit: toUnit(attached[2]), rest: attached[3] };
  } else if (spaced) {
    const [first, ...rest] = spaced[2].split(' ');
    const unit = rest.length > 0 ? toUnit(first) : null;
    result = { amount: parseAmount(spaced[1]), unit, rest: unit ? rest.join(' ') : spaced[2] };
  } else if (article && toUnit(article[1])) {
    // "a pinch of salt", "a can of tomatoes"
    result = { amount: 1, unit: toUnit(article[1]), rest: article[2] };
  }
  if (!result || result.amount === null) return null;
  return {
    amount: result.amount,
    unit: result.unit,
    rest: result.unit ? result.rest.replace(/^of\s+/i, '') : result.rest,
  };
}

/**
 * One line of the ingredients box → an ingredient. "200 g flour, sifted", "200g flour",
 * "2 eggs", "a pinch of salt", "1 1/2 cups milk", "salt (optional)".
 * The note is whatever follows the first comma.
 */
export function parseIngredientLine(line: string, section: string | null): RecipeIngredient | null {
  let text = line.trim().replace(/\s+/g, ' ');
  if (!text) return null;

  let optional = false;
  const optionalMatch = text.match(/^(.*?)\s*\(optional\)$/i);
  if (optionalMatch) {
    optional = true;
    text = optionalMatch[1];
  }

  const quantity = parseQuantityPrefix(text);
  const amount = quantity?.amount ?? null;
  const unit = quantity?.unit ?? null;
  if (quantity) text = quantity.rest;

  const comma = text.indexOf(',');
  const name = (comma >= 0 ? text.slice(0, comma) : text).trim();
  const note = comma >= 0 ? text.slice(comma + 1).trim() : '';
  if (!name) return null;

  return { name, amount, unit, note: note || null, section, optional };
}

// A line ending with ":" starts a section ("Sauce:"); the ingredients below it belong to it
export function parseIngredientsText(text: string): RecipeIngredient[] {
  let section: string | null = null;
  const ingredients: RecipeIngredient[] = [];
  for (const line of text.split('\n')) {
    const heading = line.trim().match(/^(.+):$/);
    if (heading) {
      section = heading[1].trim();
      continue;
    }
    const ingredient = parseIngredientLine(line, section);
    if (ingredient) ingredients.push(ingredient);
  }
  return ingredients;
}

// Ingredients without a section first, then each section in the order it first appears.
// The recipe page shows them this way, and the ingredients box can't express anything else.
export function groupBySection(ingredients: RecipeIngredient[]): { section: string | null; ingredients: RecipeIngredient[] }[] {
  const groups = new Map<string | null, RecipeIngredient[]>([[null, []]]);
  for (const ingredient of ingredients) {
    const section = ingredient.section || null;
    if (!groups.has(section)) groups.set(section, []);
    groups.get(section)!.push(ingredient);
  }
  return [...groups].map(([section, items]) => ({ section, ingredients: items })).filter((g) => g.ingredients.length > 0);
}

// The reverse of parseIngredientsText, for editing an existing recipe
export function formatIngredientsText(ingredients: RecipeIngredient[]): string {
  return groupBySection(ingredients)
    .map(({ section, ingredients: items }) => {
      const lines = items.map((ingredient) => {
        const quantity = [ingredient.amount != null ? String(ingredient.amount) : null, ingredient.unit]
          .filter(Boolean)
          .join(' ');
        let line = quantity ? `${quantity} ${ingredient.name}` : ingredient.name;
        if (ingredient.note) line += `, ${ingredient.note}`;
        if (ingredient.optional) line += ' (optional)';
        return line;
      });
      return section ? [`${section}:`, ...lines].join('\n') : lines.join('\n');
    })
    .join('\n\n');
}

// For reading: 0.5 → "½", 1.25 → "1¼", 0.333 → "⅓", 2.4 → "2.4"
export function formatAmount(amount: number): string {
  const whole = Math.floor(amount);
  const fraction = amount - whole;
  for (const [char, value] of Object.entries(FRACTION_CHARS)) {
    if (Math.abs(fraction - value) < 0.01) return `${whole || ''}${char}`;
  }
  if (fraction < 0.01) return String(whole);
  return String(Math.round(amount * 100) / 100);
}

// Units read as words get a plural: "2 cloves", "3 cups". Abbreviations (g, tbsp) don't.
const PLURAL_UNITS: Record<string, string> = {
  cup: 'cups', pinch: 'pinches', clove: 'cloves', can: 'cans', bunch: 'bunches', slice: 'slices', pack: 'packs',
};

// "500 g", "2", "1 tbsp", "2 cloves", "" (no amount)
export function formatQuantity(ingredient: { amount: number | null; unit?: string | null }, scale = 1): string {
  if (ingredient.amount == null) return ingredient.unit ?? '';
  const scaled = ingredient.amount * scale;
  const amount = formatAmount(scaled);
  if (!ingredient.unit) return amount;
  const unit = scaled > 1 ? (PLURAL_UNITS[ingredient.unit] ?? ingredient.unit) : ingredient.unit;
  return `${amount} ${unit}`;
}

// "1 h 15 min", "45 min"
export function formatMinutes(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (h === 0) return `${m} min`;
  return m === 0 ? `${h} h` : `${h} h ${m} min`;
}
