import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import styles from './destination-account-form.module.css';
import { useCreateOperation } from '@/features/portability/api/create-operation';
import { scheduleKey } from '@/features/portability/api/get-schedule';
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
  // The installments are already in a PENDING operation: follow that one instead.
  onExistingOperation: (operationId: string) => void;
  // While an operation is being followed nothing new can be requested.
  disabled?: boolean;
};

export const DestinationAccountForm = ({
  beneficiaryId,
  installmentIds,
  onCreated,
  onExistingOperation,
  disabled = false,
}: DestinationAccountFormProps) => {
  const queryClient = useQueryClient();
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
  const showServerFieldErrors = (error: ApiError) => {
    if (!error.errors) return;
    for (const field of FIELDS) {
      const message = error.errors[`destinationAccount.${field}`];
      if (message) setError(field, { message });
    }
  };

  const handleFailure = (error: unknown) => {
    if (!(error instanceof ApiError)) return;
    const existingOperationId = error.operationIds?.[0];
    if (error.code === 'INSTALLMENT_IN_PENDING_OPERATION' && existingOperationId) {
      operation.reset();
      onExistingOperation(existingOperationId);
      return;
    }
    if (error.code === 'INSTALLMENT_IN_CONFIRMED_OPERATION') {
      // Those installments are gone for good: show the schedule as it is now.
      void queryClient.invalidateQueries({ queryKey: scheduleKey(beneficiaryId) });
    }
    showServerFieldErrors(error);
  };

  // Awaited so the button stays disabled until the backend answers: no double submission.
  const submit = handleSubmit((destinationAccount) =>
    operation
      .mutateAsync({ beneficiaryId, installmentIds, destinationAccount })
      .then(onCreated, handleFailure),
  );

  const retry = isRetryable(operation.error) ? () => void submit() : undefined;

  return (
    <form className={styles.destinationAccountForm} onSubmit={submit} noValidate>
      <fieldset disabled={disabled}>
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
        disabled={disabled || installmentIds.length === 0 || isSubmitting}
      >
        {isSubmitting ? 'Solicitando…' : 'Solicitar portabilidade'}
      </button>
    </form>
  );
};
