import apiClient from './client';
import type { GroceryList, CreateListRequest } from './types';

export const listsApi = {
  getAll: async (): Promise<GroceryList[]> => {
    const response = await apiClient.get<GroceryList[]>('/lists');
    return response.data;
  },

  getById: async (id: string): Promise<GroceryList> => {
    const response = await apiClient.get<GroceryList>(`/lists/${id}`);
    return response.data;
  },

  create: async (data: CreateListRequest): Promise<GroceryList> => {
    const response = await apiClient.post<GroceryList>('/lists', data);
    return response.data;
  },

  update: async (id: string, name: string): Promise<void> => {
    await apiClient.patch(`/lists/${id}`, { name });
  },

  delete: async (id: string): Promise<void> => {
    await apiClient.delete(`/lists/${id}`);
  },
};