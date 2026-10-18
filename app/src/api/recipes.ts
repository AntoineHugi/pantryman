import apiClient from './client';
import type { Recipe, RecipeImportResponse, RecipeInput, RecipeSummary } from './types';

export const recipesApi = {
  getAll: async (): Promise<RecipeSummary[]> => {
    const response = await apiClient.get<RecipeSummary[]>('/recipes');
    return response.data;
  },

  getById: async (id: string): Promise<Recipe> => {
    const response = await apiClient.get<Recipe>(`/recipes/${id}`);
    return response.data;
  },

  create: async (data: RecipeInput): Promise<Recipe> => {
    const response = await apiClient.post<Recipe>('/recipes', data);
    return response.data;
  },

  // Creator only
  update: async (id: string, data: RecipeInput): Promise<Recipe> => {
    const response = await apiClient.put<Recipe>(`/recipes/${id}`, data);
    return response.data;
  },

  // Creator only
  delete: async (id: string): Promise<void> => {
    await apiClient.delete(`/recipes/${id}`);
  },

  // A parsed recipe file (docs/recipe-format.md): one recipe or an array of them
  import: async (file: unknown): Promise<RecipeImportResponse> => {
    const response = await apiClient.post<RecipeImportResponse>('/recipes/import', file);
    return response.data;
  },
};
