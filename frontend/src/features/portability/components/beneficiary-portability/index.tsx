import { useState } from 'react';
import styles from './beneficiary-portability.module.css';
import { useOperation } from '@/features/portability/api/get-operation';
import { useSchedule } from '@/features/portability/api/get-schedule';
import { DestinationAccountForm } from '@/features/portability/components/destination-account-form';
import { OperationTracker } from '@/features/portability/components/operation-tracker';
import { OperationsHistory } from '@/features/portability/components/operations-history';
import { ScheduleSection } from '@/features/portability/components/schedule-section';
import { useInstallmentSelection } from '@/features/portability/hooks/use-installment-selection';
import { useLatestPendingOperation } from '@/features/portability/hooks/use-latest-pending-operation';

type BeneficiaryPortabilityProps = {
  beneficiaryId: string;
};

type Tracking = {
  operationId: string;
  notice?: string;
};

type WorkspaceProps = BeneficiaryPortabilityProps & {
  // Newest PENDING operation found in the history, followed right away (e.g. after a reload).
  resumeOperationId: string | null;
};

const Workspace = ({ beneficiaryId, resumeOperationId }: WorkspaceProps) => {
  const [tracking, setTracking] = useState<Tracking | null>(() =>
    resumeOperationId ? { operationId: resumeOperationId } : null,
  );
  const schedule = useSchedule(beneficiaryId);
  const selection = useInstallmentSelection(schedule.data?.installments);
  const tracked = useOperation(tracking?.operationId ?? null);

  // Nothing new is requested while the followed operation is still waiting for the Central.
  const isWaiting =
    tracking !== null && !tracked.isError && (tracked.data?.status ?? 'PENDING') === 'PENDING';

  const follow = (operationId: string, notice?: string) => setTracking({ operationId, notice });

  const startOver = () => {
    setTracking(null);
    selection.clear();
  };

  return (
    <>
      <ScheduleSection
        beneficiaryId={beneficiaryId}
        selected={selection.selected}
        onToggle={selection.toggle}
        disabled={isWaiting}
      >
        <DestinationAccountForm
          beneficiaryId={beneficiaryId}
          installmentIds={selection.selected}
          onCreated={(operation) => follow(operation.id)}
          onExistingOperation={(operationId) =>
            follow(operationId, 'Já existe uma solicitação em andamento para essas parcelas.')
          }
          disabled={isWaiting}
        />
      </ScheduleSection>
      {tracking && (
        <OperationTracker
          key={tracking.operationId}
          operationId={tracking.operationId}
          onRetried={(operation) => follow(operation.id)}
          onNewRequest={startOver}
        >
          {tracking.notice && <p role="status">{tracking.notice}</p>}
        </OperationTracker>
      )}
      <OperationsHistory beneficiaryId={beneficiaryId} />
    </>
  );
};

// Everything about one beneficiary. Mounted once their history is known, so a PENDING operation
// left behind by a reload is followed from the first render.
export const BeneficiaryPortability = ({ beneficiaryId }: BeneficiaryPortabilityProps) => {
  const resumeOperationId = useLatestPendingOperation(beneficiaryId);

  return (
    <div className={styles.beneficiaryPortability}>
      {resumeOperationId === undefined ? (
        <p className={styles.loading}>Carregando…</p>
      ) : (
        <Workspace beneficiaryId={beneficiaryId} resumeOperationId={resumeOperationId} />
      )}
    </div>
  );
};
