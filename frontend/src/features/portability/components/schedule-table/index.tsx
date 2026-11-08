import styles from './schedule-table.module.css';
import type { Installment } from '@/features/portability/types';
import { formatCurrency, formatDate } from '@/features/portability/utils/format';
import {
  SITUATION_LABELS,
  situationOf,
  type InstallmentSituation,
} from '@/features/portability/utils/installment-situation';

const SITUATION_TONES: Record<InstallmentSituation, string | undefined> = {
  available: undefined,
  'in-portability': styles.pending,
  ported: styles.ported,
  unavailable: styles.unavailableTag,
};

type ScheduleTableProps = {
  installments: Installment[];
  selected: string[];
  onToggle: (installmentId: string) => void;
};

export const ScheduleTable = ({ installments, selected, onToggle }: ScheduleTableProps) => {
  if (installments.length === 0) {
    return <p className={styles.empty}>Nenhuma parcela futura para este beneficiário.</p>;
  }

  const total = installments
    .filter((installment) => selected.includes(installment.installmentId))
    .reduce((sum, installment) => sum + installment.amount, 0);

  return (
    <table className={styles.scheduleTable}>
      <caption>Agenda de descontos futuros</caption>
      <thead>
        <tr>
          <th scope="col">
            <span className={styles.visuallyHidden}>Selecionar</span>
          </th>
          <th scope="col">Parcela</th>
          <th scope="col">Convênio</th>
          <th scope="col">Valor</th>
          <th scope="col">Data de desconto</th>
          <th scope="col">Situação</th>
        </tr>
      </thead>
      <tbody>
        {installments.map((installment) => {
          const { installmentId, agreementId, amount, deductionDate, eligible } = installment;
          const situation = situationOf(installment);
          return (
            <tr key={installmentId} className={eligible ? undefined : styles.unavailable}>
              <td>
                <input
                  type="checkbox"
                  aria-label={`Selecionar parcela ${installmentId}`}
                  checked={selected.includes(installmentId)}
                  disabled={!eligible}
                  onChange={() => onToggle(installmentId)}
                />
              </td>
              <td>{installmentId}</td>
              <td>{agreementId}</td>
              <td>{formatCurrency(amount)}</td>
              <td>{formatDate(deductionDate)}</td>
              <td>
                <span className={SITUATION_TONES[situation]}>{SITUATION_LABELS[situation]}</span>
              </td>
            </tr>
          );
        })}
      </tbody>
      <tfoot>
        <tr>
          <td colSpan={6}>
            {selected.length} parcela(s) selecionada(s) · Total {formatCurrency(total)}
          </td>
        </tr>
      </tfoot>
    </table>
  );
};
