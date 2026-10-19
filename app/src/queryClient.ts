import { QueryClient } from '@tanstack/react-query';
import { createSyncStoragePersister } from '@tanstack/query-sync-storage-persister';
import type { PersistQueryClientOptions } from '@tanstack/react-query-persist-client';
import type { GroceryList } from './api/types';

const PERSIST_KEY = 'pantryman-query-cache';
const WEEK = 7 * 24 * 60 * 60 * 1000;
const PERSISTED_QUERIES = ['lists', 'households', 'recipes', 'meal-plans', 'pantry'];

// Refetches when the window regains focus, so changes from other devices show up.
// gcTime matches the persisted cache's age: queries dropped from memory aren't saved either.
// Mutations fail at once without a connection (and show an error), except item changes, which
// wait for one (`itemMutationDefaults` in useGroceryLists.ts).
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 10_000, retry: 1, gcTime: WEEK },
    mutations: { networkMode: 'always' },
  },
});

// Keep the lists page (item counts) in step with changes made on a list's own page, including
// optimistic ones: whenever a list's data (['lists', id]) changes, copy it into ['lists'].
queryClient.getQueryCache().subscribe((event) => {
  const key = event.query.queryKey;
  if (event.type !== 'updated' || event.action.type !== 'success' || key[0] !== 'lists' || key.length !== 2) return;
  const list = event.query.state.data as GroceryList | undefined;
  if (!list) return;
  queryClient.setQueryData<GroceryList[]>(['lists'], (lists) => lists?.map((l) => (l.id === list.id ? list : l)));
});

// The cache is saved to localStorage, so the app opens on the last-known lists right away
// (and shows them offline) while the server, which may be asleep, answers. Recipes too, so
// one opened before can be read in the kitchen without a connection. Item changes still waiting
// for a connection are saved with it and sent after the next start (main.tsx). After a week
// without the app being opened, all of it is dropped (maxAge), queued changes included.
export const persistOptions: Omit<PersistQueryClientOptions, 'queryClient'> = {
  persister: createSyncStoragePersister({ storage: window.localStorage, key: PERSIST_KEY }),
  maxAge: WEEK,
  // Bump when the shape of cached API data changes, to drop old caches
  buster: '1',
  dehydrateOptions: {
    shouldDehydrateQuery: (query) =>
      query.state.status === 'success' && PERSISTED_QUERIES.includes(String(query.queryKey[0])),
    shouldDehydrateMutation: (mutation) => mutation.state.isPaused && mutation.options.mutationKey?.[0] === 'items',
  },
};

// On logout: the next person to log in on this device must not see the previous user's lists
export function clearQueryCache() {
  queryClient.clear();
  try {
    localStorage.removeItem(PERSIST_KEY);
  } catch {
    // Storage unavailable: nothing was persisted
  }
}
