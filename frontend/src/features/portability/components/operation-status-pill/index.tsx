import styles from './operation-status-pill.module.css';
import type { OperationStatus } from '@/features/portability/types';

const LABELS: Record<OperationStatus, string> = {
  PENDING: 'Pendente',
  CONFIRMED: 'Confirmada',
  REJECTED: 'Rejeitada',
  FAILED: 'Falhou',
};

const TONES: Record<OperationStatus, string> = {
  PENDING: styles.pending,
  CONFIRMED: styles.confirmed,
  REJECTED: styles.rejected,
  FAILED: styles.rejected,
};

export const OperationStatusPill = ({ status }: { status: OperationStatus }) => (
  <span className={`${styles.operationStatusPill} ${TONES[status]}`}>
    {status === 'PENDING' && <span className={styles.spinner} aria-hidden="true" />}
    {LABELS[status]}
  </span>
);
