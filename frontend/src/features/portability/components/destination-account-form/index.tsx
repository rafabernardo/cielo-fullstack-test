import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import styles from './destination-account-form.module.css';
import { useCreateOperation } from '@/features/portability/api/create-operation';
import { operationsKey } from '@/features/portability/api/get-operations';
import { scheduleKey } from '@/features/portability/api/get-schedule';
import type { PortabilityOperation, Schedule } from '@/features/portability/types';
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
  const queryClient = useQueryClient();
  const operation = useCreateOperation();
  const [alreadyPending, setAlreadyPending] = useState(false);
  // An empty schedule can still be submitted, so the Central's answer (e.g. BEN_ERRO's 503) reaches the user.
  // Read from the cache, not subscribed: a second observer would refetch the schedule on mount.
  const scheduleIsEmpty =
    queryClient.getQueryData<Schedule>(scheduleKey(beneficiaryId))?.installments.length === 0;
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
    if (error.code === 'INSTALLMENT_IN_PENDING_OPERATION') {
      // Not an error: that request is already in the history, which (with the schedule) is read again.
      operation.reset();
      setAlreadyPending(true);
      void queryClient.invalidateQueries({ queryKey: scheduleKey(beneficiaryId) });
      void queryClient.invalidateQueries({ queryKey: operationsKey(beneficiaryId) });
      return;
    }
    if (error.code === 'INSTALLMENT_IN_CONFIRMED_OPERATION') {
      // Those installments are gone for good: show the schedule as it is now.
      void queryClient.invalidateQueries({ queryKey: scheduleKey(beneficiaryId) });
    }
    showServerFieldErrors(error);
  };

  // Awaited so the button stays disabled until the backend answers: no double submission.
  const submit = handleSubmit((destinationAccount) => {
    setAlreadyPending(false);
    return operation
      .mutateAsync({ beneficiaryId, installmentIds, destinationAccount })
      .then(onCreated, handleFailure);
  });

  const retry = isRetryable(operation.error) ? () => void submit() : undefined;
  // Only asked for once there is something to send. The typed account survives while hidden.
  const showAccount = installmentIds.length > 0 || scheduleIsEmpty;

  return (
    <form className={styles.destinationAccountForm} onSubmit={submit} noValidate>
      {alreadyPending && (
        <p role="status" className={styles.notice}>
          Já existe uma solicitação em andamento para essas parcelas — acompanhe no Histórico de
          portabilidade.
        </p>
      )}
      {showAccount && (
        <>
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
          <button type="submit" className={styles.submit} disabled={isSubmitting}>
            {isSubmitting ? 'Solicitando…' : 'Solicitar portabilidade'}
          </button>
        </>
      )}
    </form>
  );
};
