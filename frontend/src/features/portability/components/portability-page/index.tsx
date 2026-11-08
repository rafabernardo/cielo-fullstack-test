import { useState } from 'react';
import styles from './portability-page.module.css';
import { useSchedule } from '@/features/portability/api/get-schedule';
import { BeneficiaryForm } from '@/features/portability/components/beneficiary-form';
import { OptIn } from '@/features/portability/components/opt-in';
import { errorMessage, isRetryable } from '@/features/portability/utils/error-message';
import { ErrorBanner } from '@/components/error-banner';
import { ApiError } from '@/lib/api-client';

const BENEFICIARY_PARAM = 'beneficiary';

// The beneficiary lives in the URL so a reload resumes the same beneficiary.
const readBeneficiary = () => new URLSearchParams(window.location.search).get(BENEFICIARY_PARAM);

const writeBeneficiary = (beneficiaryId: string) => {
  const url = new URL(window.location.href);
  url.searchParams.set(BENEFICIARY_PARAM, beneficiaryId);
  window.history.replaceState(null, '', url);
};

export const PortabilityPage = () => {
  const [beneficiaryId, setBeneficiaryId] = useState(readBeneficiary);
  const schedule = useSchedule(beneficiaryId);

  const consult = (id: string) => {
    writeBeneficiary(id);
    if (id === beneficiaryId) void schedule.refetch();
    else setBeneficiaryId(id);
  };

  const needsOptIn =
    schedule.error instanceof ApiError && schedule.error.code === 'AUTHORIZATION_REQUIRED';

  return (
    <div className={styles.portabilityPage}>
      <BeneficiaryForm defaultValue={beneficiaryId ?? ''} onSubmit={consult} />
      {/* Keyed by beneficiary: switching beneficiary resets everything below. */}
      {beneficiaryId && (
        <div key={beneficiaryId} className={styles.sections}>
          {schedule.isPending && <p className={styles.muted}>Carregando agenda…</p>}
          {needsOptIn && <OptIn beneficiaryId={beneficiaryId} />}
          {schedule.isError && !needsOptIn && (
            <ErrorBanner
              onRetry={isRetryable(schedule.error) ? () => void schedule.refetch() : undefined}
            >
              {errorMessage(schedule.error)}
            </ErrorBanner>
          )}
          {schedule.isSuccess && <p>{schedule.data.installments.length} parcela(s) na agenda.</p>}
        </div>
      )}
    </div>
  );
};
