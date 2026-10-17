import type { Item } from '../api/types';

const MAX_QUANTITY = 999;

// "3 eggs", "3x eggs", "eggs x3" → { name: "eggs", quantity: 3 }. Anything else is a name with quantity 1.
export function parseItemInput(text: string): { name: string; quantity: number } {
  const trimmed = text.trim().replace(/\s+/g, ' ');
  const leading = trimmed.match(/^(\d{1,3})\s*[x×]?\s+(.+)$/i);
  if (leading) return { name: leading[2], quantity: clampQuantity(Number(leading[1])) };
  const trailing = trimmed.match(/^(.+?)\s+[x×](\d{1,3})$/i);
  if (trailing) return { name: trailing[1], quantity: clampQuantity(Number(trailing[2])) };
  return { name: trimmed, quantity: 1 };
}

function clampQuantity(quantity: number) {
  return Math.min(Math.max(quantity, 1), MAX_QUANTITY);
}

export function normalizeItemName(name: string) {
  return name.trim().replace(/\s+/g, ' ').toLowerCase();
}

// Adding "Milk" when "milk" is already on the list updates that item instead of adding a second one
export function findSameItem(items: Item[], name: string): Item | undefined {
  const key = normalizeItemName(name);
  return items.find((item) => normalizeItemName(item.name) === key);
}

// A whole number from 1 to 999, or null for anything else (empty field, 0, 2.5)
export function validQuantity(value: string): number | null {
  if (!/^\d+$/.test(value.trim())) return null;
  const quantity = Number(value);
  return quantity >= 1 && quantity <= MAX_QUANTITY ? quantity : null;
}
