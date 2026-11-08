import { useState, type FormEvent } from 'react';
import styles from './beneficiary-form.module.css';

const TEST_BENEFICIARIES = ['BEN001', 'BEN002', 'BEN_ERRO'];

type BeneficiaryFormProps = {
  defaultValue: string;
  onSubmit: (beneficiaryId: string) => void;
};

export const BeneficiaryForm = ({ defaultValue, onSubmit }: BeneficiaryFormProps) => {
  const [invalid, setInvalid] = useState(false);

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const beneficiaryId = String(new FormData(event.currentTarget).get('beneficiaryId')).trim();
    setInvalid(beneficiaryId === '');
    if (beneficiaryId !== '') onSubmit(beneficiaryId);
  };

  return (
    <form className={styles.beneficiaryForm} onSubmit={handleSubmit} noValidate>
      <label htmlFor="beneficiary-id">Beneficiário</label>
      <div className={styles.row}>
        <input
          id="beneficiary-id"
          name="beneficiaryId"
          list="test-beneficiaries"
          defaultValue={defaultValue}
          aria-invalid={invalid}
          aria-describedby="beneficiary-hint"
        />
        <datalist id="test-beneficiaries">
          {TEST_BENEFICIARIES.map((id) => (
            <option key={id} value={id} />
          ))}
        </datalist>
        <button type="submit" className={styles.submit}>
          Consultar agenda
        </button>
      </div>
      {invalid ? (
        <p id="beneficiary-hint" className={styles.error}>
          Informe o beneficiário.
        </p>
      ) : (
        <p id="beneficiary-hint" className={styles.hint}>
          BEN001 → confirma · BEN002 → rejeita · BEN_ERRO → Central indisponível
        </p>
      )}
    </form>
  );
};
