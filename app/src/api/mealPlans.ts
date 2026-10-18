import apiClient from './client';
import type { ConfirmMealPlanResponse, CreateMealPlanRequest, MealPlan, MealSlot } from './types';

const slotPath = (planId: string, slot: MealSlot) => `/meal-plans/${planId}/entries/${slot.date}/${slot.meal}`;

export const mealPlansApi = {
  getAll: async (): Promise<MealPlan[]> => {
    const response = await apiClient.get<MealPlan[]>('/meal-plans');
    return response.data;
  },

  getById: async (id: string): Promise<MealPlan> => {
    const response = await apiClient.get<MealPlan>(`/meal-plans/${id}`);
    return response.data;
  },

  create: async (data: CreateMealPlanRequest): Promise<MealPlan> => {
    const response = await apiClient.post<MealPlan>('/meal-plans', data);
    return response.data;
  },

  delete: async (id: string): Promise<void> => {
    await apiClient.delete(`/meal-plans/${id}`);
  },

  // Replaces whatever is in the slot. Servings default to the plan's people.
  setMeal: async (planId: string, slot: MealSlot, recipeId: string, servings?: number): Promise<MealPlan> => {
    const response = await apiClient.put<MealPlan>(slotPath(planId, slot), { recipeId, servings });
    return response.data;
  },

  removeMeal: async (planId: string, slot: MealSlot): Promise<MealPlan> => {
    const response = await apiClient.delete<MealPlan>(slotPath(planId, slot));
    return response.data;
  },

  // If a meal is already in `to`, the two swap
  moveMeal: async (planId: string, from: MealSlot, to: MealSlot): Promise<MealPlan> => {
    const response = await apiClient.post<MealPlan>(`/meal-plans/${planId}/move`, { from, to });
    return response.data;
  },

  // Creates the grocery list "Meal plan - 20.10/26.10"
  confirm: async (planId: string): Promise<ConfirmMealPlanResponse> => {
    const response = await apiClient.post<ConfirmMealPlanResponse>(`/meal-plans/${planId}/confirm`);
    return response.data;
  },
};
