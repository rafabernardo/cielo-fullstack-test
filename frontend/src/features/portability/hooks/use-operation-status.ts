import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { operationKey, useOperation } from '@/features/portability/api/get-operation';
import { scheduleKey } from '@/features/portability/api/get-schedule';
import type { PortabilityOperation } from '@/features/portability/types';

/**
 * Follows an operation through its Server-Sent Events until it leaves PENDING, writing each status
 * into the operation's cache. `connectionLost` turns true when the stream gives up for good.
 */
export const useOperationStatus = (id: string) => {
  const queryClient = useQueryClient();
  const operation = useOperation(id);
  const [connectionLost, setConnectionLost] = useState(false);
  const isFinished = operation.data !== undefined && operation.data.status !== 'PENDING';

  useEffect(() => {
    if (isFinished) return;

    const source = new EventSource(`/api/portability-operations/${encodeURIComponent(id)}/events`);
    source.addEventListener('status', (event) => {
      const current: PortabilityOperation = JSON.parse(event.data);
      queryClient.setQueryData(operationKey(id), current);
      if (current.status !== 'PENDING') {
        source.close();
        // Installments were taken for good or freed again.
        void queryClient.invalidateQueries({ queryKey: scheduleKey(current.beneficiaryId) });
      }
    });
    // EventSource reconnects on its own (e.g. after the 60 s server timeout); CLOSED means it gave up.
    source.onerror = () => setConnectionLost(source.readyState === EventSource.CLOSED);

    return () => source.close();
  }, [id, isFinished, queryClient]);

  return { operation, connectionLost };
};
