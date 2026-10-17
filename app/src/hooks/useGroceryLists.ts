import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { listsApi } from '../api/lists';
import { itemsApi } from '../api/items';
import type { CreateItemRequest, CreateListRequest, GroceryList, Item } from '../api/types';

// Query keys. Invalidating `all` also refreshes every list detail.
export const listKeys = {
  all: ['lists'] as const,
  detail: (listId: string) => ['lists', listId] as const,
};

export function useLists() {
  return useQuery({ queryKey: listKeys.all, queryFn: listsApi.getAll });
}

export function useList(listId: string) {
  return useQuery({
    queryKey: listKeys.detail(listId),
    queryFn: () => listsApi.getById(listId),
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
    onSuccess: (_, listId) => {
      queryClient.removeQueries({ queryKey: listKeys.detail(listId) });
      return queryClient.invalidateQueries({ queryKey: listKeys.all });
    },
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
    onSettled: () => queryClient.invalidateQueries({ queryKey: key }),
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
