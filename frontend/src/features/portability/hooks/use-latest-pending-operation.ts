import { useOperations } from '@/features/portability/api/get-operations';

/**
 * Id of the beneficiary's newest PENDING operation, from the first page of their history (newest
 * first): `undefined` while unknown, `null` when there is none or the history can't be read.
 */
export const useLatestPendingOperation = (beneficiaryId: string) => {
  const operations = useOperations(beneficiaryId, 0);
  if (operations.isError) return null;
  if (!operations.data) return undefined;
  return operations.data.content.find((operation) => operation.status === 'PENDING')?.id ?? null;
};
