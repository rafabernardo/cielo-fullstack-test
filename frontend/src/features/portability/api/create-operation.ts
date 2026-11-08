import { useMutation, useQueryClient } from '@tanstack/react-query';
import { operationKey } from './get-operation';
import { operationsKey } from './get-operations';
import type { DestinationAccount, PortabilityOperation } from '@/features/portability/types';
import { apiPost } from '@/lib/api-client';

export type CreateOperationInput = {
  beneficiaryId: string;
  installmentIds: string[];
  destinationAccount: DestinationAccount;
};

export const createOperation = (input: CreateOperationInput): Promise<PortabilityOperation> =>
  apiPost('/api/portability-operations', input);

// The 202 body is the operation itself: cache it so following it needs no extra read, and
// refresh the history so the new operation shows on top.
export const useCreateOperation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createOperation,
    onSuccess: (operation) => {
      queryClient.setQueryData(operationKey(operation.id), operation);
      void queryClient.invalidateQueries({ queryKey: operationsKey(operation.beneficiaryId) });
    },
  });
};
