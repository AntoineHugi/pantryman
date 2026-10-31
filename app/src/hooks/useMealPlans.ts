import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { mealPlansApi } from '../api/mealPlans';
import type { CreateMealPlanRequest, MealChoice, MealPlan, MealSlot } from '../api/types';
import { listKeys } from './useGroceryLists';

// Invalidating `all` also refreshes every plan
export const mealPlanKeys = {
  all: ['meal-plans'] as const,
  detail: (planId: string) => ['meal-plans', planId] as const,
};

const sameSlot = (a: MealSlot, b: MealSlot) => a.date === b.date && a.meal === b.meal;

export function useMealPlans() {
  return useQuery({ queryKey: mealPlanKeys.all, queryFn: mealPlansApi.getAll });
}

export function useMealPlan(planId: string) {
  const queryClient = useQueryClient();
  return useQuery({
    queryKey: mealPlanKeys.detail(planId),
    queryFn: () => mealPlansApi.getById(planId),
    // Opens at once from the plans page's data
    placeholderData: () => queryClient.getQueryData<MealPlan[]>(mealPlanKeys.all)?.find((p) => p.id === planId),
  });
}

export function useCreateMealPlan() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateMealPlanRequest) => mealPlansApi.create(data),
    onSuccess: (plan) => {
      queryClient.setQueryData(mealPlanKeys.detail(plan.id), plan);
      return queryClient.invalidateQueries({ queryKey: mealPlanKeys.all, exact: true });
    },
  });
}

export function useDeleteMealPlan() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (planId: string) => mealPlansApi.delete(planId),
    onSuccess: (_, planId) => {
      queryClient.setQueryData<MealPlan[]>(mealPlanKeys.all, (plans) => plans?.filter((p) => p.id !== planId));
      queryClient.removeQueries({ queryKey: mealPlanKeys.detail(planId) });
    },
  });
}

// Every change to a plan's meals answers with the whole plan
function usePlanChange<TVars>(planId: string, mutationFn: (vars: TVars) => Promise<MealPlan>, optimistic?: (plan: MealPlan, vars: TVars) => MealPlan) {
  const queryClient = useQueryClient();
  const key = mealPlanKeys.detail(planId);
  return useMutation({
    mutationFn,
    onMutate: async (vars: TVars) => {
      if (!optimistic) return {};
      await queryClient.cancelQueries({ queryKey: key });
      const previous = queryClient.getQueryData<MealPlan>(key);
      if (previous) queryClient.setQueryData(key, optimistic(previous, vars));
      return { previous };
    },
    onError: (_err, _vars, context) => {
      if (context?.previous) queryClient.setQueryData(key, context.previous);
    },
    onSuccess: (plan) => {
      queryClient.setQueryData(key, plan);
      return queryClient.invalidateQueries({ queryKey: mealPlanKeys.all, exact: true });
    },
  });
}

export function useSetMeal(planId: string) {
  return usePlanChange(planId, ({ slot, choice, servings }: { slot: MealSlot; choice: MealChoice; servings?: number }) =>
    mealPlansApi.setMeal(planId, slot, choice, servings)
  );
}

export function useRemoveMeal(planId: string) {
  return usePlanChange(
    planId,
    (slot: MealSlot) => mealPlansApi.removeMeal(planId, slot),
    (plan, slot) => ({ ...plan, entries: plan.entries.filter((e) => !sameSlot(e, slot)) })
  );
}

// Moves at once on screen; a meal already in `to` swaps places
export function useMoveMeal(planId: string) {
  return usePlanChange(
    planId,
    ({ from, to }: { from: MealSlot; to: MealSlot }) => mealPlansApi.moveMeal(planId, from, to),
    (plan, { from, to }) => ({
      ...plan,
      entries: plan.entries.map((e) =>
        sameSlot(e, from) ? { ...e, ...to } : sameSlot(e, to) ? { ...e, date: from.date, meal: from.meal } : e
      ),
    })
  );
}

export function useConfirmMealPlan(planId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => mealPlansApi.confirm(planId),
    onSuccess: ({ plan, list }) => {
      queryClient.setQueryData(mealPlanKeys.detail(planId), plan);
      queryClient.setQueryData(listKeys.detail(list.id), list);
      return Promise.all([
        queryClient.invalidateQueries({ queryKey: mealPlanKeys.all, exact: true }),
        queryClient.invalidateQueries({ queryKey: listKeys.all, exact: true }),
      ]);
    },
  });
}
