import { useQuery, useQueryClient } from '@tanstack/react-query';
import type { PortabilityOperation } from '@/features/portability/types';
import { apiGet, type QueryContext } from '@/lib/api-client';

export const getOperation = (id: string, context?: QueryContext): Promise<PortabilityOperation> =>
  apiGet(`/portability-operations/${encodeURIComponent(id)}`, context);

export const operationKey = (id: string) => ['operation', id];

// Read once; afterwards the status stream (useOperationStatus) keeps the cache current.
export const useOperation = (id: string | null) => {
  const queryClient = useQueryClient();
  return useQuery({
    queryKey: operationKey(id ?? ''),
    queryFn: async (context) => {
      const operation = await getOperation(id!, context);
      const cached = queryClient.getQueryData<PortabilityOperation>(operationKey(id!));
      // A read that raced the stream must not take a finished operation back to PENDING.
      return cached && cached.status !== 'PENDING' ? cached : operation;
    },
    enabled: id !== null,
    staleTime: Infinity,
  });
};
