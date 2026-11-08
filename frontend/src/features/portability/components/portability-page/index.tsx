import { useState } from 'react';
import styles from './portability-page.module.css';
import { useOperation } from '@/features/portability/api/get-operation';
import { useSchedule } from '@/features/portability/api/get-schedule';
import { BeneficiaryForm } from '@/features/portability/components/beneficiary-form';
import { DestinationAccountForm } from '@/features/portability/components/destination-account-form';
import { OperationTracker } from '@/features/portability/components/operation-tracker';
import { ScheduleSection } from '@/features/portability/components/schedule-section';
import { useInstallmentSelection } from '@/features/portability/hooks/use-installment-selection';
import { readBeneficiary, writeBeneficiary } from '@/features/portability/utils/beneficiary-url';

type Tracking = {
  operationId: string;
  notice?: string;
};

export const PortabilityPage = () => {
  const [beneficiaryId, setBeneficiaryId] = useState(readBeneficiary);
  const [tracking, setTracking] = useState<Tracking | null>(null);
  const schedule = useSchedule(beneficiaryId);
  const selection = useInstallmentSelection(schedule.data?.installments);
  const tracked = useOperation(tracking?.operationId ?? null);

  // Nothing new is requested while the followed operation is still waiting for the Central.
  const isWaiting =
    tracking !== null && !tracked.isError && (tracked.data?.status ?? 'PENDING') === 'PENDING';

  const startOver = () => {
    setTracking(null);
    selection.clear();
  };

  const consult = (id: string) => {
    writeBeneficiary(id);
    if (id === beneficiaryId) {
      void schedule.refetch();
    } else {
      setBeneficiaryId(id);
      startOver();
    }
  };

  const follow = (operationId: string, notice?: string) => setTracking({ operationId, notice });

  return (
    <div className={styles.portabilityPage}>
      <BeneficiaryForm defaultValue={beneficiaryId ?? ''} onSubmit={consult} />
      {/* Keyed by beneficiary: switching beneficiary resets everything below. */}
      {beneficiaryId && (
        <ScheduleSection
          key={beneficiaryId}
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
      )}
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
    </div>
  );
};
