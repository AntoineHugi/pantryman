import apiClient from './client';
import type { AddPantryItemRequest, PantryItem } from './types';

export const pantryApi = {
  // householdId null: the user's personal items
  getAll: async (householdId: string | null): Promise<PantryItem[]> => {
    const response = await apiClient.get<PantryItem[]>('/pantry-items', { params: householdId ? { householdId } : {} });
    return response.data;
  },

  // A name that's already there is updated (e.g. moved from defaults to staples)
  add: async (data: AddPantryItemRequest): Promise<PantryItem> => {
    const response = await apiClient.post<PantryItem>('/pantry-items', data);
    return response.data;
  },

  delete: async (id: string): Promise<void> => {
    await apiClient.delete(`/pantry-items/${id}`);
  },
};
