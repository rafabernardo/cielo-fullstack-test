import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import styles from './portability-page.module.css';
import { scheduleKey } from '@/features/portability/api/get-schedule';
import { BeneficiaryForm } from '@/features/portability/components/beneficiary-form';
import { BeneficiaryPortability } from '@/features/portability/components/beneficiary-portability';
import { readBeneficiary, writeBeneficiary } from '@/features/portability/utils/beneficiary-url';

export const PortabilityPage = () => {
  const queryClient = useQueryClient();
  const [beneficiaryId, setBeneficiaryId] = useState(readBeneficiary);

  const consult = (id: string) => {
    writeBeneficiary(id);
    if (id === beneficiaryId) {
      void queryClient.invalidateQueries({ queryKey: scheduleKey(id) });
    } else {
      setBeneficiaryId(id);
    }
  };

  return (
    <div className={styles.portabilityPage}>
      <BeneficiaryForm defaultValue={beneficiaryId ?? ''} onSubmit={consult} />
      {/* Keyed by beneficiary: switching beneficiary resets everything below. */}
      {beneficiaryId && (
        <BeneficiaryPortability key={beneficiaryId} beneficiaryId={beneficiaryId} />
      )}
    </div>
  );
};
