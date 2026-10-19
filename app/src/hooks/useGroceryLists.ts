import {
  useMutation,
  useQuery,
  useQueryClient,
  type MutateOptions,
  type MutationOptions,
  type QueryClient,
} from '@tanstack/react-query';
import { listsApi } from '../api/lists';
import { itemsApi } from '../api/items';
import type { AddRecipeToListRequest, CreateItemRequest, CreateListRequest, GroceryList, Item } from '../api/types';
import { isNetworkError, markOffline } from '../network';
import { newId } from '../utils/ids';

// Query keys. Invalidating `all` also refreshes every list detail.
export const listKeys = {
  all: ['lists'] as const,
  detail: (listId: string) => ['lists', listId] as const,
};

// Shared by every mutation that changes a list's items, so polling can pause while they run
// (including changes waiting for a connection)
const itemMutationKey = (listId: string) => ['items', listId] as const;

// How often the open list is refetched, so items ticked by others on a shared list show up
const LIST_POLL_MS = 5000;

// More than `besides` item changes of this list are pending (paused ones count)
function isChangingItems(queryClient: QueryClient, listId: string, besides = 0) {
  return queryClient.isMutating({ mutationKey: itemMutationKey(listId) }) > besides;
}

export function useLists() {
  return useQuery({ queryKey: listKeys.all, queryFn: listsApi.getAll });
}

export function useList(listId: string) {
  const queryClient = useQueryClient();
  return useQuery({
    queryKey: listKeys.detail(listId),
    queryFn: () => listsApi.getById(listId),
    // The lists query already has every list with its items: start from that, so the list opens
    // at once. Initial (not placeholder) data, so ticking items works before the first fetch.
    initialData: () => queryClient.getQueryData<GroceryList[]>(listKeys.all)?.find((l) => l.id === listId),
    initialDataUpdatedAt: () => queryClient.getQueryState(listKeys.all)?.dataUpdatedAt,
    // Only while the page is visible. Paused during item changes: a poll answered before the
    // change reaches the server would briefly undo it on screen.
    refetchInterval: () => (isChangingItems(queryClient, listId) ? false : LIST_POLL_MS),
  });
}

export function useCreateList() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateListRequest) => listsApi.create(data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: listKeys.all }),
  });
}

export function useDeleteList() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (listId: string) => listsApi.delete(listId),
    // Gone from the lists page at once
    onMutate: async (listId) => {
      await queryClient.cancelQueries({ queryKey: listKeys.all });
      queryClient.setQueryData<GroceryList[]>(listKeys.all, (lists) => lists?.filter((l) => l.id !== listId));
    },
    onSuccess: (_, listId) => {
      queryClient.removeQueries({ queryKey: listKeys.detail(listId) });
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: listKeys.all, exact: true }),
  });
}

export function useRenameList(listId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (name: string) => listsApi.update(listId, name),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: listKeys.all }),
  });
}

export function useSetListHousehold(listId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (householdId: string | null) => listsApi.setHousehold(listId, householdId),
    onSuccess: (list) => {
      queryClient.setQueryData(listKeys.detail(listId), list);
      return queryClient.invalidateQueries({ queryKey: listKeys.all });
    },
  });
}

/**
 * Changes to a list's items work offline. Each change is a plain object, applied to the cached list
 * at once and sent in order (one queue, `scope`). Without a connection the queue pauses, is saved
 * with the query cache (`queryClient.ts`) and is sent once the server answers again, even after the
 * app was closed. New items get their ID here, so later changes to them can be queued too.
 */
export type ItemChange =
  | { type: 'add'; listId: string; item: Item }
  | { type: 'update'; listId: string; itemId: string; data: Partial<Item> }
  | { type: 'delete'; listId: string; itemIds: string[] }
  // Undo for deleting: the same items back, with their IDs (so the order stays and a retry can't add them twice)
  | { type: 'restore'; listId: string; items: Item[] };

// Each request can be sent twice without harm (a retry after the answer was lost)
async function sendItemChange(change: ItemChange): Promise<void> {
  const { listId } = change;
  switch (change.type) {
    case 'add': {
      const { id, name, quantity, unit } = change.item;
      await itemsApi.create(listId, { id, name, quantity, unit });
      return;
    }
    case 'update':
      await itemsApi.update(listId, change.itemId, change.data);
      return;
    case 'delete':
      // Skips items that are already gone
      await itemsApi.deleteMany(listId, change.itemIds);
      return;
    case 'restore':
      await Promise.all(
        change.items.map(({ id, name, quantity, unit, isChecked }) =>
          itemsApi.create(listId, { id, name, quantity, unit, isChecked })
        )
      );
  }
}

function applyItemChange(items: Item[], change: ItemChange): Item[] {
  switch (change.type) {
    case 'add':
      return [...items.filter((it) => it.id !== change.item.id), change.item];
    case 'update':
      return items.map((it) => (it.id === change.itemId ? { ...it, ...change.data } : it));
    case 'delete':
      return items.filter((it) => !change.itemIds.includes(it.id));
    case 'restore': {
      const ids = new Set(change.items.map((it) => it.id));
      return [...items.filter((it) => !ids.has(it.id)), ...change.items];
    }
  }
}

// Registered for every item mutation (`setMutationDefaults(['items'], ...)` in main.tsx), so a change
// restored from storage after a restart knows how to send itself.
export function itemMutationDefaults(queryClient: QueryClient): MutationOptions<void, Error, ItemChange> {
  return {
    mutationFn: sendItemChange,
    scope: { id: 'items' },
    networkMode: 'online',
    // No answer: wait (paused) until the server can be reached. Errors from the server aren't retried.
    retry: (_failures, error) => {
      if (!isNetworkError(error)) return false;
      markOffline();
      return true;
    },
    retryDelay: 1000,
    onMutate: async (change) => {
      const key = listKeys.detail(change.listId);
      await queryClient.cancelQueries({ queryKey: key });
      queryClient.setQueryData<GroceryList>(key, (list) =>
        list ? { ...list, items: applyItemChange(list.items, change) } : list
      );
    },
    // No rollback on error: later queued changes are already on screen, and a snapshot would undo
    // them too. The refetch after the last change shows what the server has.
    onSettled: (_data, _error, change) => {
      if (isChangingItems(queryClient, change.listId, 1)) return;
      return queryClient.invalidateQueries({ queryKey: listKeys.detail(change.listId) });
    },
  };
}

function useItemChange(listId: string) {
  return useMutation<void, Error, ItemChange>({ mutationKey: itemMutationKey(listId) });
}

type ItemMutateOptions = MutateOptions<void, Error, ItemChange>;

export function useAddItem(listId: string) {
  const { mutate } = useItemChange(listId);
  return {
    mutate: (data: CreateItemRequest, options?: ItemMutateOptions) =>
      mutate({ type: 'add', listId, item: { ...data, id: newId(), isChecked: false } }, options),
  };
}

export function useUpdateItem(listId: string) {
  const { mutate } = useItemChange(listId);
  return {
    mutate: ({ itemId, data }: { itemId: string; data: Partial<Item> }, options?: ItemMutateOptions) =>
      mutate({ type: 'update', listId, itemId, data }, options),
  };
}

export function useDeleteItem(listId: string) {
  const { mutate } = useItemChange(listId);
  return {
    mutate: (itemId: string, options?: ItemMutateOptions) => mutate({ type: 'delete', listId, itemIds: [itemId] }, options),
  };
}

// "Clear ticked items": the IDs shown as ticked, deleted in one request
export function useDeleteItems(listId: string) {
  const { mutate } = useItemChange(listId);
  return {
    mutate: (itemIds: string[], options?: ItemMutateOptions) => mutate({ type: 'delete', listId, itemIds }, options),
  };
}

// Undo for deleting
export function useRestoreItems(listId: string) {
  const { mutate } = useItemChange(listId);
  return {
    mutate: (items: Item[], options?: ItemMutateOptions) => mutate({ type: 'restore', listId, items }, options),
  };
}

// From a recipe page: the list isn't open, so there is no optimistic update; the answer has the new list
export function useAddRecipeToList() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ listId, data }: { listId: string; data: AddRecipeToListRequest }) => listsApi.addRecipe(listId, data),
    onSuccess: ({ list }) => {
      queryClient.setQueryData(listKeys.detail(list.id), list);
      return queryClient.invalidateQueries({ queryKey: listKeys.all, exact: true });
    },
  });
}
