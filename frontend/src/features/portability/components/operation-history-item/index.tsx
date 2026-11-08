import { useId, useState } from 'react';
import styles from './operation-history-item.module.css';
import { useCreateOperation } from '@/features/portability/api/create-operation';
import { useSchedule } from '@/features/portability/api/get-schedule';
import { OperationStatusPill } from '@/features/portability/components/operation-status-pill';
import { useElapsedSeconds } from '@/features/portability/hooks/use-elapsed-seconds';
import { useOperationStatus } from '@/features/portability/hooks/use-operation-status';
import type { PortabilityOperation } from '@/features/portability/types';
import { errorMessage } from '@/features/portability/utils/error-message';
import { formatDateTime } from '@/features/portability/utils/format';
import { ErrorBanner } from '@/components/error-banner';

type OperationHistoryItemProps = {
  operation: PortabilityOperation;
};

const Waiting = ({ since }: { since: string }) => {
  const seconds = useElapsedSeconds(since);
  return <span className={styles.muted}>Aguardando a Central · {seconds}s</span>;
};

// One operation as table rows; while PENDING it keeps itself up to date through the status stream.
export const OperationHistoryItem = ({ operation: listed }: OperationHistoryItemProps) => {
  const [open, setOpen] = useState(false);
  const detailsId = useId();
  const { operation, connectionLost } = useOperationStatus(listed.id);
  const retry = useCreateOperation();
  const {
    status,
    beneficiaryId,
    createdAt,
    updatedAt,
    installmentIds,
    destinationAccount,
    rejectionReason,
  } = operation.data ?? listed;
  const schedule = useSchedule(beneficiaryId);

  // A FAILED operation changed nothing; it can be sent again while its installments are still free
  // (the retry, once created, holds them and the button goes away).
  const canRetry =
    status === 'FAILED' &&
    installmentIds.every((id) =>
      schedule.data?.installments.some(
        (installment) => installment.installmentId === id && installment.eligible,
      ),
    );
  const resubmit = () => retry.mutate({ beneficiaryId, installmentIds, destinationAccount });

  return (
    <>
      <tr className={styles.operationHistoryItem}>
        <td>
          <button
            type="button"
            className={styles.toggle}
            aria-expanded={open}
            aria-controls={detailsId}
            aria-label={`Detalhes da solicitação de ${formatDateTime(createdAt)}`}
            onClick={() => setOpen((current) => !current)}
          >
            ›
          </button>
        </td>
        <td>{formatDateTime(createdAt)}</td>
        <td>{installmentIds.join(', ')}</td>
        <td>
          <div className={styles.status}>
            <span aria-live="polite">
              <OperationStatusPill status={status} />
            </span>
            {status === 'PENDING' && <Waiting since={createdAt} />}
            {status === 'PENDING' && connectionLost && (
              <button type="button" onClick={() => void operation.refetch()}>
                Atualizar
              </button>
            )}
            {status === 'REJECTED' && (
              <span className={styles.bad}>{rejectionReason ?? 'Motivo não informado'}</span>
            )}
            {status === 'FAILED' && (
              <span className={styles.bad}>Falha na comunicação com a Central</span>
            )}
          </div>
        </td>
        <td className={styles.actions}>
          {canRetry && (
            <button type="button" disabled={retry.isPending} onClick={resubmit}>
              Tentar novamente
            </button>
          )}
        </td>
      </tr>
      {retry.isError && (
        <tr className={styles.detail}>
          <td />
          <td colSpan={4}>
            <ErrorBanner>{errorMessage(retry.error)}</ErrorBanner>
          </td>
        </tr>
      )}
      {open && (
        <tr id={detailsId} className={styles.detail}>
          <td />
          <td colSpan={4}>
            Conta de destino: Banco {destinationAccount.bankCode} · Agência{' '}
            {destinationAccount.branch} · Conta {destinationAccount.accountNumber} · Atualizado em{' '}
            {formatDateTime(updatedAt)}
          </td>
        </tr>
      )}
    </>
  );
};
