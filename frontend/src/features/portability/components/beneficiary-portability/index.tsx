import styles from './beneficiary-portability.module.css';
import { useAuthorization } from '@/features/portability/api/get-authorization';
import { useSchedule } from '@/features/portability/api/get-schedule';
import { DestinationAccountForm } from '@/features/portability/components/destination-account-form';
import { OptIn } from '@/features/portability/components/opt-in';
import { OperationsHistory } from '@/features/portability/components/operations-history';
import { ScheduleSection } from '@/features/portability/components/schedule-section';
import { useInstallmentSelection } from '@/features/portability/hooks/use-installment-selection';
import { errorMessage, isRetryable } from '@/features/portability/utils/error-message';
import { ErrorBanner } from '@/components/error-banner';

type BeneficiaryPortabilityProps = {
  beneficiaryId: string;
};

// Everything about one beneficiary. The authorization (opt-in) is checked first, and nothing else
// about them is read before they authorized.
export const BeneficiaryPortability = ({ beneficiaryId }: BeneficiaryPortabilityProps) => {
  const authorization = useAuthorization(beneficiaryId);
  const authorized = authorization.data?.authorized === true;
  const schedule = useSchedule(authorized ? beneficiaryId : null);
  const selection = useInstallmentSelection(schedule.data?.installments);

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
    if (!authorized) {
      return <OptIn beneficiaryId={beneficiaryId} />;
    }
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
            // Its installments are held now; what is left can be requested right away.
            onCreated={selection.clear}
          />
        </ScheduleSection>
        <OperationsHistory beneficiaryId={beneficiaryId} />
      </>
    );
  };

  return <div className={styles.beneficiaryPortability}>{content()}</div>;
};
