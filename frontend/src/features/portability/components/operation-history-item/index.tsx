import styles from './operation-history-item.module.css';
import { OperationStatusPill } from '@/features/portability/components/operation-status-pill';
import { useOperationStatus } from '@/features/portability/hooks/use-operation-status';
import type { PortabilityOperation } from '@/features/portability/types';
import { formatDateTime } from '@/features/portability/utils/format';

type OperationHistoryItemProps = {
  operation: PortabilityOperation;
};

// One past operation; while PENDING it keeps itself up to date through the status stream.
export const OperationHistoryItem = ({ operation: listed }: OperationHistoryItemProps) => {
  const { operation } = useOperationStatus(listed.id);
  const { status, createdAt, updatedAt, installmentIds, destinationAccount, rejectionReason } =
    operation.data ?? listed;

  return (
    <li className={styles.operationHistoryItem}>
      <details>
        <summary>
          <span>{formatDateTime(createdAt)}</span>
          <OperationStatusPill status={status} />
          <span className={styles.muted}>{installmentIds.length} parcela(s)</span>
        </summary>
        <dl>
          <dt>Parcelas</dt>
          <dd>{installmentIds.join(', ')}</dd>
          <dt>Conta de destino</dt>
          <dd>
            Banco {destinationAccount.bankCode} · Agência {destinationAccount.branch} · Conta{' '}
            {destinationAccount.accountNumber}
          </dd>
          {rejectionReason && (
            <>
              <dt>Motivo da rejeição</dt>
              <dd>{rejectionReason}</dd>
            </>
          )}
          <dt>Atualizado em</dt>
          <dd>{formatDateTime(updatedAt)}</dd>
        </dl>
      </details>
    </li>
  );
};
