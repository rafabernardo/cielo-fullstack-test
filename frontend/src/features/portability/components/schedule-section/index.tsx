import type { ReactNode } from 'react';
import styles from './schedule-section.module.css';
import { useSchedule } from '@/features/portability/api/get-schedule';
import { OptIn } from '@/features/portability/components/opt-in';
import { ScheduleTable } from '@/features/portability/components/schedule-table';
import { errorMessage, isRetryable } from '@/features/portability/utils/error-message';
import { ErrorBanner } from '@/components/error-banner';
import { ApiError } from '@/lib/api-client';

type ScheduleSectionProps = {
  beneficiaryId: string;
  selected: string[];
  onToggle: (installmentId: string) => void;
  // Rendered below the table once there is something to select (e.g. the request form).
  children?: ReactNode;
};

const isAuthorizationRequired = (error: unknown) =>
  error instanceof ApiError && error.code === 'AUTHORIZATION_REQUIRED';

// Shows the schedule, or what stands in its way: loading, missing opt-in or a failure.
export const ScheduleSection = ({
  beneficiaryId,
  selected,
  onToggle,
  children,
}: ScheduleSectionProps) => {
  const schedule = useSchedule(beneficiaryId);

  if (schedule.isPending) {
    return <p className={styles.loading}>Carregando agenda…</p>;
  }

  if (isAuthorizationRequired(schedule.error)) {
    return <OptIn beneficiaryId={beneficiaryId} />;
  }

  if (schedule.isError) {
    const retry = isRetryable(schedule.error) ? () => void schedule.refetch() : undefined;
    return <ErrorBanner onRetry={retry}>{errorMessage(schedule.error)}</ErrorBanner>;
  }

  const { installments } = schedule.data;
  return (
    <div className={styles.scheduleSection}>
      <ScheduleTable installments={installments} selected={selected} onToggle={onToggle} />
      {installments.length > 0 && children}
    </div>
  );
};
