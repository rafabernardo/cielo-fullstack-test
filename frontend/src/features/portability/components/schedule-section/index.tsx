import type { ReactNode } from 'react';
import styles from './schedule-section.module.css';
import { useSchedule } from '@/features/portability/api/get-schedule';
import { ScheduleTable } from '@/features/portability/components/schedule-table';
import { errorMessage, isRetryable } from '@/features/portability/utils/error-message';
import { ErrorBanner } from '@/components/error-banner';

type ScheduleSectionProps = {
  beneficiaryId: string;
  selected: string[];
  onToggle: (installmentId: string) => void;
  // Rendered below the table once it has loaded (e.g. the request form).
  children?: ReactNode;
};

// Shows the schedule, or what stands in its way: loading or a failure. The opt-in is handled above.
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

  if (schedule.isError) {
    const retry = isRetryable(schedule.error) ? () => void schedule.refetch() : undefined;
    return <ErrorBanner onRetry={retry}>{errorMessage(schedule.error)}</ErrorBanner>;
  }

  const { installments } = schedule.data;
  return (
    <div className={styles.scheduleSection}>
      <ScheduleTable installments={installments} selected={selected} onToggle={onToggle} />
      {children}
    </div>
  );
};
