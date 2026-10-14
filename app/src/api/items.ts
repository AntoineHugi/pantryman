import apiClient from './client';
import type { Item, CreateItemRequest } from './types';

export const itemsApi = {
  getAll: async (listId: string): Promise<Item[]> => {
    const response = await apiClient.get<Item[]>(`/lists/${listId}/items`);
    return response.data;
  },

  create: async (listId: string, data: CreateItemRequest): Promise<Item> => {
    const response = await apiClient.post<Item>(`/lists/${listId}/items`, data);
    return response.data;
  },

  update: async (listId: string, itemId: string, data: Partial<Item>): Promise<void> => {
    await apiClient.patch(`/lists/${listId}/items/${itemId}`, data);
  },

  delete: async (listId: string, itemId: string): Promise<void> => {
    await apiClient.delete(`/lists/${listId}/items/${itemId}`);
  },
};