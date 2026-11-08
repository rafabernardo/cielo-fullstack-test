import { useMutation, useQueryClient } from '@tanstack/react-query';
import { scheduleKey } from './get-schedule';
import type { Authorization } from '@/features/portability/types';
import { apiPost } from '@/lib/api-client';

export const createAuthorization = (beneficiaryId: string): Promise<Authorization> =>
  apiPost('/api/authorizations', { beneficiaryId });

// Once authorized, the schedule that answered AUTHORIZATION_REQUIRED is fetched again.
export const useCreateAuthorization = (beneficiaryId: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => createAuthorization(beneficiaryId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: scheduleKey(beneficiaryId) }),
  });
};
