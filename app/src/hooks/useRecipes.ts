import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { recipesApi } from '../api/recipes';
import type { Recipe, RecipeInput, RecipeSummary } from '../api/types';

// Invalidating `all` also refreshes every recipe detail
export const recipeKeys = {
  all: ['recipes'] as const,
  detail: (recipeId: string) => ['recipes', recipeId] as const,
};

export function useRecipes() {
  return useQuery({ queryKey: recipeKeys.all, queryFn: recipesApi.getAll });
}

export function useRecipe(recipeId: string) {
  return useQuery({ queryKey: recipeKeys.detail(recipeId), queryFn: () => recipesApi.getById(recipeId) });
}

export function useCreateRecipe() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: RecipeInput) => recipesApi.create(data),
    onSuccess: (recipe) => {
      queryClient.setQueryData(recipeKeys.detail(recipe.id), recipe);
      return queryClient.invalidateQueries({ queryKey: recipeKeys.all, exact: true });
    },
  });
}

export function useUpdateRecipe() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ recipeId, data }: { recipeId: string; data: RecipeInput }) => recipesApi.update(recipeId, data),
    onSuccess: (recipe) => {
      queryClient.setQueryData<Recipe>(recipeKeys.detail(recipe.id), recipe);
      return queryClient.invalidateQueries({ queryKey: recipeKeys.all, exact: true });
    },
  });
}

export function useDeleteRecipe() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (recipeId: string) => recipesApi.delete(recipeId),
    onSuccess: (_, recipeId) => {
      queryClient.setQueryData<RecipeSummary[]>(recipeKeys.all, (recipes) => recipes?.filter((r) => r.id !== recipeId));
      queryClient.removeQueries({ queryKey: recipeKeys.detail(recipeId) });
    },
  });
}

export function useImportRecipes() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (file: unknown) => recipesApi.import(file),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: recipeKeys.all, exact: true }),
  });
}
