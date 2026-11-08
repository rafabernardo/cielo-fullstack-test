import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { operationKey } from './get-operation';
import type { Page, PortabilityOperation } from '@/features/portability/types';
import { apiGet } from '@/lib/api-client';

export const PAGE_SIZE = 5;

export const getOperations = (
  beneficiaryId: string,
  page: number,
  signal?: AbortSignal,
): Promise<Page<PortabilityOperation>> =>
  apiGet(
    `/api/beneficiaries/${encodeURIComponent(beneficiaryId)}/portability-operations?page=${page}&size=${PAGE_SIZE}`,
    signal,
  );

// Prefix of every page of the beneficiary's operations, for invalidation.
export const operationsKey = (beneficiaryId: string) => ['operations', beneficiaryId];

export const useOperations = (beneficiaryId: string, page: number) => {
  const queryClient = useQueryClient();
  return useQuery({
    queryKey: [...operationsKey(beneficiaryId), page],
    queryFn: async ({ signal }) => {
      const operations = await getOperations(beneficiaryId, page, signal);
      // Each row is followed through its own cache entry (see useOperationStatus).
      for (const operation of operations.content) {
        const cached = queryClient.getQueryData<PortabilityOperation>(operationKey(operation.id));
        if (!cached || cached.status === 'PENDING') {
          queryClient.setQueryData(operationKey(operation.id), operation);
        }
      }
      return operations;
    },
    placeholderData: keepPreviousData,
    // Our own changes invalidate it (new operation, final status); this only avoids reading the
    // same page again when another component starts showing it.
    staleTime: 30_000,
  });
};
