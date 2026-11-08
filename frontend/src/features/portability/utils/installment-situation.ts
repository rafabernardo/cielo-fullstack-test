import type { Installment } from '@/features/portability/types';

export type InstallmentSituation = 'available' | 'in-portability' | 'ported' | 'unavailable';

export const SITUATION_LABELS: Record<InstallmentSituation, string> = {
  available: 'Disponível',
  'in-portability': 'Em portabilidade',
  ported: 'Portada',
  unavailable: 'Indisponível',
};

// What the installment means for portability: free, held by a pending or a confirmed operation,
// or not open at the Central.
export const situationOf = ({ heldBy, eligible }: Installment): InstallmentSituation => {
  if (heldBy?.status === 'CONFIRMED') return 'ported';
  if (heldBy?.status === 'PENDING') return 'in-portability';
  return eligible ? 'available' : 'unavailable';
};
