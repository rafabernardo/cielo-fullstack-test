import { useQuery } from '@tanstack/react-query';
import type { Schedule } from '@/features/portability/types';
import { apiGet, type QueryContext } from '@/lib/api-client';

export const getSchedule = (beneficiaryId: string, context?: QueryContext): Promise<Schedule> =>
  apiGet(`/api/beneficiaries/${encodeURIComponent(beneficiaryId)}/schedule`, context);

export const scheduleKey = (beneficiaryId: string) => ['schedule', beneficiaryId];

export const useSchedule = (beneficiaryId: string | null) =>
  useQuery({
    queryKey: scheduleKey(beneficiaryId ?? ''),
    queryFn: (context) => getSchedule(beneficiaryId!, context),
    enabled: beneficiaryId !== null,
    // Read once when the beneficiary's workspace mounts; its components reuse that answer
    // (or its error) instead of reading again. Our own changes invalidate it.
    refetchOnMount: false,
    retryOnMount: false,
  });
