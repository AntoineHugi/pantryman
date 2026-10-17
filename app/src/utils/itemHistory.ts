import { normalizeItemName } from './itemInput';

// Items this user has added, for suggestions. Kept on the device (localStorage) per user:
// items are deleted when lists are cleared, so the server doesn't remember them.
export interface HistoryEntry {
  name: string;
  count: number;
  lastUsed: number;
}

const MAX_ENTRIES = 300;

const storageKey = (userId: string) => `pantryman-item-history:${userId}`;

export function loadItemHistory(userId: string | null): HistoryEntry[] {
  if (!userId) return [];
  try {
    const parsed = JSON.parse(localStorage.getItem(storageKey(userId)) ?? '[]');
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function recordItemAdded(userId: string | null, name: string) {
  if (!userId) return;
  const key = normalizeItemName(name);
  const history = loadItemHistory(userId);
  const existing = history.find((entry) => normalizeItemName(entry.name) === key);
  const updated: HistoryEntry[] = existing
    ? history.map((entry) =>
        entry === existing ? { name: name.trim(), count: entry.count + 1, lastUsed: Date.now() } : entry
      )
    : [...history, { name: name.trim(), count: 1, lastUsed: Date.now() }];
  // Drop the least-used entries once the history gets long
  const trimmed = updated.sort((a, b) => b.count - a.count || b.lastUsed - a.lastUsed).slice(0, MAX_ENTRIES);
  try {
    localStorage.setItem(storageKey(userId), JSON.stringify(trimmed));
  } catch {
    // Storage full or blocked: suggestions are a convenience
  }
}
