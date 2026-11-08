import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import styles from './destination-account-form.module.css';
import { useCreateOperation } from '@/features/portability/api/create-operation';
import type { PortabilityOperation } from '@/features/portability/types';
import {
  destinationAccountSchema,
  type DestinationAccountValues,
} from '@/features/portability/utils/destination-account-schema';
import { errorMessage, isRetryable } from '@/features/portability/utils/error-message';
import { ErrorBanner } from '@/components/error-banner';
import { TextField } from '@/components/text-field';
import { ApiError } from '@/lib/api-client';

const FIELDS = ['bankCode', 'branch', 'accountNumber'] as const;

type DestinationAccountFormProps = {
  beneficiaryId: string;
  installmentIds: string[];
  onCreated: (operation: PortabilityOperation) => void;
};

export const DestinationAccountForm = ({
  beneficiaryId,
  installmentIds,
  onCreated,
}: DestinationAccountFormProps) => {
  const operation = useCreateOperation();
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<DestinationAccountValues>({
    resolver: zodResolver(destinationAccountSchema),
    defaultValues: { bankCode: '', branch: '', accountNumber: '' },
  });

  // Field errors from the backend (`destinationAccount.bankCode`) go under their input.
  const showServerFieldErrors = (error: unknown) => {
    if (!(error instanceof ApiError) || !error.errors) return;
    for (const field of FIELDS) {
      const message = error.errors[`destinationAccount.${field}`];
      if (message) setError(field, { message });
    }
  };

  // Awaited so the button stays disabled until the backend answers: no double submission.
  const submit = handleSubmit((destinationAccount) =>
    operation
      .mutateAsync({ beneficiaryId, installmentIds, destinationAccount })
      .then(onCreated, showServerFieldErrors),
  );

  const retry = isRetryable(operation.error) ? () => void submit() : undefined;

  return (
    <form className={styles.destinationAccountForm} onSubmit={submit} noValidate>
      <fieldset>
        <legend>Conta de destino</legend>
        <TextField
          label="Banco"
          inputMode="numeric"
          error={errors.bankCode?.message}
          {...register('bankCode')}
        />
        <TextField
          label="Agência"
          inputMode="numeric"
          error={errors.branch?.message}
          {...register('branch')}
        />
        <TextField
          label="Conta"
          error={errors.accountNumber?.message}
          {...register('accountNumber')}
        />
      </fieldset>
      {operation.isError && (
        <ErrorBanner onRetry={retry}>{errorMessage(operation.error)}</ErrorBanner>
      )}
      <button
        type="submit"
        className={styles.submit}
        disabled={installmentIds.length === 0 || isSubmitting}
      >
        {isSubmitting ? 'Solicitando…' : 'Solicitar portabilidade'}
      </button>
    </form>
  );
};
