import { useQuery } from '@tanstack/react-query';
import type { Schedule } from '@/features/portability/types';
import { apiGet } from '@/lib/api-client';

export const getSchedule = (beneficiaryId: string): Promise<Schedule> =>
  apiGet(`/api/beneficiaries/${encodeURIComponent(beneficiaryId)}/schedule`);

export const scheduleKey = (beneficiaryId: string) => ['schedule', beneficiaryId];

export const useSchedule = (beneficiaryId: string | null) =>
  useQuery({
    queryKey: scheduleKey(beneficiaryId ?? ''),
    queryFn: () => getSchedule(beneficiaryId!),
    enabled: beneficiaryId !== null,
  });
