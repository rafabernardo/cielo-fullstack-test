import { useMutation } from '@tanstack/react-query';
import type { DestinationAccount, PortabilityOperation } from '@/features/portability/types';
import { apiPost } from '@/lib/api-client';

export type CreateOperationInput = {
  beneficiaryId: string;
  installmentIds: string[];
  destinationAccount: DestinationAccount;
};

export const createOperation = (input: CreateOperationInput): Promise<PortabilityOperation> =>
  apiPost('/api/portability-operations', input);

export const useCreateOperation = () => useMutation({ mutationFn: createOperation });
