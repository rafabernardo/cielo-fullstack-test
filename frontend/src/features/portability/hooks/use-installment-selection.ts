import { useState } from 'react';
import type { Installment } from '@/features/portability/types';

// Selected installment ids. Only ids still eligible in `installments` count: a refetched schedule
// may no longer offer one that was selected.
export const useInstallmentSelection = (installments: Installment[] = []) => {
  const [selected, setSelected] = useState<string[]>([]);

  const toggle = (installmentId: string) => {
    setSelected((current) => {
      const isSelected = current.includes(installmentId);
      if (isSelected) {
        return current.filter((id) => id !== installmentId);
      }
      return [...current, installmentId];
    });
  };

  const clear = () => setSelected([]);

  const eligibleSelected = selected.filter((id) =>
    installments.some((installment) => installment.installmentId === id && installment.eligible),
  );

  return { selected: eligibleSelected, toggle, clear };
};
