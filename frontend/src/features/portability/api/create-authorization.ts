import { useMutation, useQueryClient } from '@tanstack/react-query';
import { authorizationKey } from './get-authorization';
import type { Authorization } from '@/features/portability/types';
import { apiPost } from '@/lib/api-client';

export const createAuthorization = (beneficiaryId: string): Promise<Authorization> =>
  apiPost('/api/authorizations', { beneficiaryId });

// Once authorized, the beneficiary is known to be authorized: no need to ask again.
export const useCreateAuthorization = (beneficiaryId: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => createAuthorization(beneficiaryId),
    onSuccess: () =>
      queryClient.setQueryData(authorizationKey(beneficiaryId), {
        beneficiaryId,
        authorized: true,
      }),
  });
};
