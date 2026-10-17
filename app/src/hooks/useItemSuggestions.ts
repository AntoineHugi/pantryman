import { useMemo } from 'react';
import type { Item } from '../api/types';
import { loadItemHistory } from '../utils/itemHistory';
import { normalizeItemName } from '../utils/itemInput';
import { useAuth } from './useAuth';
import { useLists } from './useGroceryLists';

const MAX_SUGGESTIONS = 8;

// Item names to offer while adding: what this user added before (most often first), plus items on
// their other lists. With a search, names containing it (those starting with it first);
// without one, the usual items. Items already on the list and not ticked are left out.
export function useItemSuggestions(listItems: Item[], search: string): string[] {
  const { userId } = useAuth();
  const { data: lists = [] } = useLists();

  return useMemo(() => {
    const onList = new Set(listItems.filter((i) => !i.isChecked).map((i) => normalizeItemName(i.name)));
    const candidates = new Map<string, { name: string; score: number }>();
    for (const entry of loadItemHistory(userId)) {
      candidates.set(normalizeItemName(entry.name), { name: entry.name, score: entry.count });
    }
    for (const item of lists.flatMap((l) => l.items)) {
      const key = normalizeItemName(item.name);
      if (!candidates.has(key)) candidates.set(key, { name: item.name, score: 0 });
    }

    const query = normalizeItemName(search);
    const matches = [...candidates.entries()]
      .filter(([key]) => !onList.has(key) && (!query || key.includes(query)))
      .sort(([keyA, a], [keyB, b]) => {
        if (query) {
          const startsA = keyA.startsWith(query) ? 0 : 1;
          const startsB = keyB.startsWith(query) ? 0 : 1;
          if (startsA !== startsB) return startsA - startsB;
        }
        return b.score - a.score || a.name.localeCompare(b.name);
      });
    return matches.slice(0, MAX_SUGGESTIONS).map(([, c]) => c.name);
  }, [listItems, lists, search, userId]);
}
