import { useMutation, useQueryClient } from '@tanstack/react-query';
import { operationKey } from './get-operation';
import { operationsKey } from './get-operations';
import { scheduleKey } from './get-schedule';
import type { DestinationAccount, PortabilityOperation } from '@/features/portability/types';
import { apiPost } from '@/lib/api-client';

export type CreateOperationInput = {
  beneficiaryId: string;
  installmentIds: string[];
  destinationAccount: DestinationAccount;
};

export const createOperation = (input: CreateOperationInput): Promise<PortabilityOperation> =>
  apiPost('/portability-operations', input);

// The 202 body is the operation itself: cache it so following it needs no extra read. The schedule
// (its installments are held now) and the history (it shows on top) are read again.
export const useCreateOperation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createOperation,
    onSuccess: (operation) => {
      queryClient.setQueryData(operationKey(operation.id), operation);
      void queryClient.invalidateQueries({ queryKey: scheduleKey(operation.beneficiaryId) });
      void queryClient.invalidateQueries({ queryKey: operationsKey(operation.beneficiaryId) });
    },
  });
};
