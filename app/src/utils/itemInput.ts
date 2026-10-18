import type { Item } from '../api/types';
import { formatQuantity, parseQuantityPrefix } from './recipeText';

// Matches ItemService.MAX_QUANTITY on the backend
const MAX_QUANTITY = 100_000;

export interface ParsedItem {
  name: string;
  quantity: number;
  // null for a count
  unit: string | null;
}

// "3 eggs", "500 g flour", "0.5 l milk", "3x eggs", "eggs x3". Anything else is a name with quantity 1.
export function parseItemInput(text: string): ParsedItem {
  const trimmed = text.trim().replace(/\s+/g, ' ');
  const prefix = parseQuantityPrefix(trimmed);
  if (prefix && prefix.amount > 0) {
    return { name: prefix.rest, quantity: clampQuantity(prefix.amount), unit: prefix.unit };
  }
  const leading = trimmed.match(/^(\d{1,3})\s*[x×]\s*(.+)$/i);
  if (leading) return { name: leading[2], quantity: clampQuantity(Number(leading[1])), unit: null };
  const trailing = trimmed.match(/^(.+?)\s+[x×](\d{1,3})$/i);
  if (trailing) return { name: trailing[1], quantity: clampQuantity(Number(trailing[2])), unit: null };
  return { name: trimmed, quantity: 1, unit: null };
}

function clampQuantity(quantity: number) {
  return Math.min(Math.round(quantity * 1000) / 1000, MAX_QUANTITY);
}

export function normalizeItemName(name: string) {
  return name.trim().replace(/\s+/g, ' ').toLowerCase();
}

// Adding "Milk" when "milk" is already on the list updates that item instead of adding a second one
export function findSameItem(items: Item[], name: string): Item | undefined {
  const key = normalizeItemName(name);
  return items.find((item) => normalizeItemName(item.name) === key);
}

// More than 0 and up to 100000, with at most 3 decimals ("0.5", "0,5", "250"). Null for anything else.
export function validQuantity(value: string): number | null {
  const text = value.trim().replace(',', '.');
  if (!/^\d+(\.\d{1,3})?$/.test(text)) return null;
  const quantity = Number(text);
  return quantity > 0 && quantity <= MAX_QUANTITY ? quantity : null;
}

// What the list shows next to the name: "500 g", "2 cans", "×3", or nothing for a single item
export function formatItemQuantity(item: Pick<Item, 'quantity' | 'unit'>): string {
  if (item.unit) return formatQuantity({ amount: item.quantity, unit: item.unit });
  return item.quantity === 1 ? '' : `×${formatQuantity({ amount: item.quantity })}`;
}
