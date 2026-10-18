import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { listsApi } from '../api/lists';
import { itemsApi } from '../api/items';
import type { AddRecipeToListRequest, CreateItemRequest, CreateListRequest, GroceryList, Item } from '../api/types';

// Query keys. Invalidating `all` also refreshes every list detail.
export const listKeys = {
  all: ['lists'] as const,
  detail: (listId: string) => ['lists', listId] as const,
};

// Shared by every mutation that changes a list's items, so polling can pause while they run
const itemMutationKey = (listId: string) => ['items', listId] as const;

// How often the open list is refetched, so items ticked by others on a shared list show up
const LIST_POLL_MS = 5000;

function isChangingItems(queryClient: QueryClient, listId: string) {
  return queryClient.isMutating({ mutationKey: itemMutationKey(listId) }) > 0;
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

export function useAddItem(listId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationKey: itemMutationKey(listId),
    mutationFn: (data: CreateItemRequest) => itemsApi.create(listId, data),
    onSuccess: (item) => {
      queryClient.setQueryData<GroceryList>(listKeys.detail(listId), (list) =>
        list ? { ...list, items: [...list.items, item] } : list
      );
      return queryClient.invalidateQueries({ queryKey: listKeys.detail(listId) });
    },
  });
}

// Applies `change` to the cached list right away, and rolls back if the request fails.
function useOptimisticItemMutation<TVars>(
  listId: string,
  mutationFn: (vars: TVars) => Promise<void>,
  change: (items: Item[], vars: TVars) => Item[]
) {
  const queryClient = useQueryClient();
  const key = listKeys.detail(listId);
  return useMutation({
    mutationKey: itemMutationKey(listId),
    mutationFn,
    onMutate: async (vars: TVars) => {
      await queryClient.cancelQueries({ queryKey: key });
      const previous = queryClient.getQueryData<GroceryList>(key);
      queryClient.setQueryData<GroceryList>(key, (list) =>
        list ? { ...list, items: change(list.items, vars) } : list
      );
      return { previous };
    },
    onError: (_err, _vars, context) => {
      if (context?.previous) queryClient.setQueryData(key, context.previous);
    },
    // When several changes are in flight (ticking items quickly), refetch once after the last,
    // so an earlier refetch doesn't briefly undo the later ones
    onSettled: () => {
      if (queryClient.isMutating({ mutationKey: itemMutationKey(listId) }) === 1) {
        return queryClient.invalidateQueries({ queryKey: key });
      }
    },
  });
}

export function useUpdateItem(listId: string) {
  return useOptimisticItemMutation(
    listId,
    ({ itemId, data }: { itemId: string; data: Partial<Item> }) => itemsApi.update(listId, itemId, data),
    (items, { itemId, data }) => items.map((it) => (it.id === itemId ? { ...it, ...data } : it))
  );
}

export function useDeleteItem(listId: string) {
  return useOptimisticItemMutation(
    listId,
    (itemId: string) => itemsApi.delete(listId, itemId),
    (items, itemId) => items.filter((it) => it.id !== itemId)
  );
}

// "Clear ticked items": the IDs shown as ticked, deleted in one request
export function useDeleteItems(listId: string) {
  return useOptimisticItemMutation(
    listId,
    async (itemIds: string[]) => {
      await itemsApi.deleteMany(listId, itemIds);
    },
    (items, itemIds) => items.filter((it) => !itemIds.includes(it.id))
  );
}

// Undo for deleting: adds the items back (as new items, so they move to the end of the list)
export function useRestoreItems(listId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationKey: itemMutationKey(listId),
    mutationFn: (items: Item[]) =>
      Promise.all(
        items.map(async (item) => {
          const created = await itemsApi.create(listId, { name: item.name, quantity: item.quantity, unit: item.unit });
          if (item.isChecked) await itemsApi.update(listId, created.id, { isChecked: true });
        })
      ),
    onSettled: () => queryClient.invalidateQueries({ queryKey: listKeys.detail(listId) }),
  });
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
