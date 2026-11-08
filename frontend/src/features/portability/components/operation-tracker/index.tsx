import type { ReactNode } from 'react';
import styles from './operation-tracker.module.css';
import { useCreateOperation } from '@/features/portability/api/create-operation';
import { OperationStatusPill } from '@/features/portability/components/operation-status-pill';
import { useElapsedSeconds } from '@/features/portability/hooks/use-elapsed-seconds';
import { useOperationStatus } from '@/features/portability/hooks/use-operation-status';
import type { PortabilityOperation } from '@/features/portability/types';
import { errorMessage } from '@/features/portability/utils/error-message';
import { ErrorBanner } from '@/components/error-banner';

type OperationTrackerProps = {
  operationId: string;
  // Called with the operation created by "Tentar novamente" after a FAILED one.
  onRetried: (operation: PortabilityOperation) => void;
  onNewRequest: () => void;
  // A notice shown above the status, e.g. why this operation is being followed.
  children?: ReactNode;
};

const Waiting = ({ since }: { since: string }) => {
  const seconds = useElapsedSeconds(since);
  return <p className={styles.muted}>Aguardando a Central… {seconds}s</p>;
};

export const OperationTracker = ({
  operationId,
  onRetried,
  onNewRequest,
  children,
}: OperationTrackerProps) => {
  const { operation, connectionLost } = useOperationStatus(operationId);
  const retry = useCreateOperation();

  if (operation.isPending) {
    return <p className={styles.muted}>Carregando operação…</p>;
  }
  if (operation.isError) {
    return (
      <section className={styles.operationTracker}>
        <ErrorBanner>{errorMessage(operation.error)}</ErrorBanner>
        <button type="button" className={styles.primary} onClick={onNewRequest}>
          Nova portabilidade
        </button>
      </section>
    );
  }

  const { status, installmentIds, rejectionReason, createdAt, beneficiaryId, destinationAccount } =
    operation.data;
  const resubmit = () =>
    retry.mutate({ beneficiaryId, installmentIds, destinationAccount }, { onSuccess: onRetried });

  return (
    <section className={styles.operationTracker} aria-labelledby="operation-title">
      {children}
      <div aria-live="polite" className={styles.status}>
        <h2 id="operation-title">
          Operação <code>{operationId}</code> <OperationStatusPill status={status} />
        </h2>
        {status === 'PENDING' && <Waiting since={createdAt} />}
        {status === 'CONFIRMED' && (
          <p className={styles.ok}>
            Portabilidade confirmada para as parcelas {installmentIds.join(', ')}.
          </p>
        )}
        {status === 'REJECTED' && (
          <p className={styles.bad}>
            Portabilidade rejeitada pela Central: {rejectionReason ?? 'motivo não informado'}.
          </p>
        )}
        {status === 'FAILED' && <p className={styles.bad}>Falha na comunicação com a Central.</p>}
      </div>

      {status === 'PENDING' && connectionLost && (
        <button type="button" onClick={() => void operation.refetch()}>
          Atualizar
        </button>
      )}
      {retry.isError && <ErrorBanner>{errorMessage(retry.error)}</ErrorBanner>}
      {status !== 'PENDING' && (
        <div className={styles.actions}>
          {status === 'FAILED' && (
            <button type="button" disabled={retry.isPending} onClick={resubmit}>
              Tentar novamente
            </button>
          )}
          <button type="button" className={styles.primary} onClick={onNewRequest}>
            Nova portabilidade
          </button>
        </div>
      )}
    </section>
  );
};
