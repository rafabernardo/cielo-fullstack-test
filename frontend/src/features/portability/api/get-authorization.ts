import { useQuery } from '@tanstack/react-query';
import type { AuthorizationStatus } from '@/features/portability/types';
import { apiGet, type QueryContext } from '@/lib/api-client';

export const getAuthorization = (
  beneficiaryId: string,
  context?: QueryContext,
): Promise<AuthorizationStatus> =>
  apiGet(`/beneficiaries/${encodeURIComponent(beneficiaryId)}/authorization`, context);

export const authorizationKey = (beneficiaryId: string) => ['authorization', beneficiaryId];

export const useAuthorization = (beneficiaryId: string) =>
  useQuery({
    queryKey: authorizationKey(beneficiaryId),
    queryFn: (context) => getAuthorization(beneficiaryId, context),
  });
