import apiClient from './client';
import type { Household } from './types';

export const householdsApi = {
  getAll: async (): Promise<Household[]> => {
    const response = await apiClient.get<Household[]>('/households');
    return response.data;
  },

  create: async (name: string): Promise<Household> => {
    const response = await apiClient.post<Household>('/households', { name });
    return response.data;
  },

  join: async (inviteCode: string): Promise<Household> => {
    const response = await apiClient.post<Household>('/households/join', { inviteCode });
    return response.data;
  },

  rename: async (id: string, name: string): Promise<Household> => {
    const response = await apiClient.patch<Household>(`/households/${id}`, { name });
    return response.data;
  },

  // Owner only. Also replaces the invite code.
  removeMember: async (id: string, userId: string): Promise<void> => {
    await apiClient.delete(`/households/${id}/members/${userId}`);
  },

  regenerateInviteCode: async (id: string): Promise<Household> => {
    const response = await apiClient.post<Household>(`/households/${id}/invite-code`);
    return response.data;
  },

  leave: async (id: string): Promise<void> => {
    await apiClient.post(`/households/${id}/leave`);
  },
};
