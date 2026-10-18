import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { pantryApi } from '../api/pantry';
import type { AddPantryItemRequest, GroceryList, PantryItem } from '../api/types';
import { normalizeItemName } from '../utils/itemInput';

// householdId null: the user's personal items
export const pantryKeys = {
  scope: (householdId: string | null) => ['pantry', householdId ?? 'personal'] as const,
};

export function usePantry(householdId: string | null, enabled = true) {
  return useQuery({ queryKey: pantryKeys.scope(householdId), queryFn: () => pantryApi.getAll(householdId), enabled });
}

// A shared list uses its household's items, a personal list the user's own
export function usePantryForList(list: GroceryList | undefined) {
  return usePantry(list?.householdId ?? null, !!list);
}

export function useStapleNames(list: GroceryList | undefined): Set<string> {
  const { data = [] } = usePantryForList(list);
  return new Set(data.filter((item) => item.kind === 'staple').map((item) => normalizeItemName(item.name)));
}

export function useAddPantryItem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: AddPantryItemRequest) => pantryApi.add(data),
    onSuccess: (item) =>
      queryClient.setQueryData<PantryItem[]>(pantryKeys.scope(item.householdId), (items = []) =>
        items.some((i) => i.id === item.id) ? items.map((i) => (i.id === item.id ? item : i)) : [...items, item]
      ),
  });
}

export function useDeletePantryItem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (item: PantryItem) => pantryApi.delete(item.id),
    onMutate: (item) =>
      queryClient.setQueryData<PantryItem[]>(pantryKeys.scope(item.householdId), (items) => items?.filter((i) => i.id !== item.id)),
    onError: (_err, item) => queryClient.invalidateQueries({ queryKey: pantryKeys.scope(item.householdId) }),
  });
}
