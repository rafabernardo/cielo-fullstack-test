import { useState } from 'react';
import styles from './opt-in.module.css';
import { useCreateAuthorization } from '@/features/portability/api/create-authorization';
import { errorMessage } from '@/features/portability/utils/error-message';
import { ErrorBanner } from '@/components/error-banner';

type OptInProps = {
  beneficiaryId: string;
};

export const OptIn = ({ beneficiaryId }: OptInProps) => {
  const [consented, setConsented] = useState(false);
  const authorization = useCreateAuthorization(beneficiaryId);

  return (
    <section className={styles.optIn} aria-labelledby="opt-in-title">
      <h2 id="opt-in-title">Autorização do beneficiário</h2>
      <p>
        O beneficiário <strong>{beneficiaryId}</strong> ainda não autorizou a consulta da agenda.
      </p>
      <label className={styles.consent}>
        <input
          type="checkbox"
          checked={consented}
          onChange={(event) => setConsented(event.target.checked)}
        />
        Autorizo a consulta e a portabilidade das minhas parcelas
      </label>
      <button
        type="button"
        className={styles.submit}
        disabled={!consented || authorization.isPending}
        onClick={() => authorization.mutate()}
      >
        {authorization.isPending ? 'Autorizando…' : 'Autorizar'}
      </button>
      {authorization.isError && <ErrorBanner>{errorMessage(authorization.error)}</ErrorBanner>}
    </section>
  );
};
