import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { householdsApi } from '../api/households';
import { listKeys } from './useGroceryLists';

export const householdKeys = {
  all: ['households'] as const,
};

export function useHouseholds() {
  return useQuery({ queryKey: householdKeys.all, queryFn: householdsApi.getAll });
}

export function useCreateHousehold() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (name: string) => householdsApi.create(name),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: householdKeys.all }),
  });
}

// Joining or leaving changes which lists the user can see
function useMembershipMutation<TVars, TResult>(mutationFn: (vars: TVars) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: householdKeys.all }),
        queryClient.invalidateQueries({ queryKey: listKeys.all }),
      ]),
  });
}

export function useJoinHousehold() {
  return useMembershipMutation((inviteCode: string) => householdsApi.join(inviteCode));
}

export function useLeaveHousehold() {
  return useMembershipMutation((householdId: string) => householdsApi.leave(householdId));
}

export function useRenameHousehold() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ householdId, name }: { householdId: string; name: string }) =>
      householdsApi.rename(householdId, name),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: householdKeys.all }),
  });
}

export function useRemoveMember() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ householdId, userId }: { householdId: string; userId: string }) =>
      householdsApi.removeMember(householdId, userId),
    // The owner takes over the removed member's shared lists
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: householdKeys.all }),
        queryClient.invalidateQueries({ queryKey: listKeys.all }),
      ]),
  });
}

export function useRegenerateInviteCode() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (householdId: string) => householdsApi.regenerateInviteCode(householdId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: householdKeys.all }),
  });
}
