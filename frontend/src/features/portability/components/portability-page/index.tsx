import { useState } from 'react';
import styles from './portability-page.module.css';
import { useSchedule } from '@/features/portability/api/get-schedule';
import { BeneficiaryForm } from '@/features/portability/components/beneficiary-form';
import { DestinationAccountForm } from '@/features/portability/components/destination-account-form';
import { ScheduleSection } from '@/features/portability/components/schedule-section';
import { readBeneficiary, writeBeneficiary } from '@/features/portability/utils/beneficiary-url';

export const PortabilityPage = () => {
  const [beneficiaryId, setBeneficiaryId] = useState(readBeneficiary);
  const [selected, setSelected] = useState<string[]>([]);
  const [operationId, setOperationId] = useState<string | null>(null);
  const schedule = useSchedule(beneficiaryId);

  const consult = (id: string) => {
    writeBeneficiary(id);
    if (id === beneficiaryId) {
      void schedule.refetch();
    } else {
      setBeneficiaryId(id);
      setSelected([]);
      setOperationId(null);
    }
  };

  const toggle = (installmentId: string) => {
    setSelected((current) => {
      const isSelected = current.includes(installmentId);
      if (isSelected) {
        return current.filter((id) => id !== installmentId);
      }
      return [...current, installmentId];
    });
  };

  // A refetched schedule may no longer offer a selected installment: only eligible ones count.
  const eligibleSelected = selected.filter((id) =>
    schedule.data?.installments.some((i) => i.installmentId === id && i.eligible),
  );

  return (
    <div className={styles.portabilityPage}>
      <BeneficiaryForm defaultValue={beneficiaryId ?? ''} onSubmit={consult} />
      {/* Keyed by beneficiary: switching beneficiary resets everything below. */}
      {beneficiaryId && (
        <ScheduleSection
          key={beneficiaryId}
          beneficiaryId={beneficiaryId}
          selected={eligibleSelected}
          onToggle={toggle}
        >
          <DestinationAccountForm
            beneficiaryId={beneficiaryId}
            installmentIds={eligibleSelected}
            onCreated={(operation) => setOperationId(operation.id)}
          />
        </ScheduleSection>
      )}
      {operationId && <p role="status">Operação {operationId} criada.</p>}
    </div>
  );
};
