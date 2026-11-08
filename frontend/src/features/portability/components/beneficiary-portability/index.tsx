import { useState } from 'react';
import styles from './beneficiary-portability.module.css';
import { useAuthorization } from '@/features/portability/api/get-authorization';
import { useSchedule } from '@/features/portability/api/get-schedule';
import { DestinationAccountForm } from '@/features/portability/components/destination-account-form';
import { OperationTracker } from '@/features/portability/components/operation-tracker';
import { OptIn } from '@/features/portability/components/opt-in';
import { OperationsHistory } from '@/features/portability/components/operations-history';
import { ScheduleSection } from '@/features/portability/components/schedule-section';
import { useInstallmentSelection } from '@/features/portability/hooks/use-installment-selection';
import { useLatestPendingOperation } from '@/features/portability/hooks/use-latest-pending-operation';
import { errorMessage, isRetryable } from '@/features/portability/utils/error-message';
import { ErrorBanner } from '@/components/error-banner';

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
      >
        <DestinationAccountForm
          beneficiaryId={beneficiaryId}
          installmentIds={selection.selected}
          onCreated={(operation) => {
            // Its installments are held now; what is left can be requested right away.
            selection.clear();
            follow(operation.id);
          }}
          onExistingOperation={(operationId) =>
            follow(operationId, 'Já existe uma solicitação em andamento para essas parcelas.')
          }
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

// Waits for the history, so a PENDING operation left behind by a reload is followed from the
// first render.
const AuthorizedBeneficiary = ({ beneficiaryId }: BeneficiaryPortabilityProps) => {
  const resumeOperationId = useLatestPendingOperation(beneficiaryId);
  if (resumeOperationId === undefined) {
    return <p className={styles.loading}>Carregando…</p>;
  }
  return <Workspace beneficiaryId={beneficiaryId} resumeOperationId={resumeOperationId} />;
};

// Everything about one beneficiary. The authorization (opt-in) is checked first, and nothing else
// about them is read before they authorized.
export const BeneficiaryPortability = ({ beneficiaryId }: BeneficiaryPortabilityProps) => {
  const authorization = useAuthorization(beneficiaryId);

  const content = () => {
    if (authorization.isPending) {
      return <p className={styles.loading}>Carregando…</p>;
    }
    if (authorization.isError) {
      const retry = isRetryable(authorization.error)
        ? () => void authorization.refetch()
        : undefined;
      return <ErrorBanner onRetry={retry}>{errorMessage(authorization.error)}</ErrorBanner>;
    }
    if (!authorization.data.authorized) {
      return <OptIn beneficiaryId={beneficiaryId} />;
    }
    return <AuthorizedBeneficiary beneficiaryId={beneficiaryId} />;
  };

  return <div className={styles.beneficiaryPortability}>{content()}</div>;
};
