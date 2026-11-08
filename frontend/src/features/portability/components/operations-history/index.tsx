import { useState } from 'react';
import styles from './operations-history.module.css';
import { useOperations } from '@/features/portability/api/get-operations';
import { OperationHistoryItem } from '@/features/portability/components/operation-history-item';
import { errorMessage, isRetryable } from '@/features/portability/utils/error-message';
import { ErrorBanner } from '@/components/error-banner';
import { Pagination } from '@/components/pagination';

type OperationsHistoryProps = {
  beneficiaryId: string;
};

const OperationsList = ({ beneficiaryId }: OperationsHistoryProps) => {
  const [page, setPage] = useState(0);
  const operations = useOperations(beneficiaryId, page);

  if (operations.isPending) {
    return <p className={styles.muted}>Carregando portabilidades…</p>;
  }

  if (operations.isError) {
    const retry = isRetryable(operations.error) ? () => void operations.refetch() : undefined;
    return <ErrorBanner onRetry={retry}>{errorMessage(operations.error)}</ErrorBanner>;
  }

  const { content, page: pageInfo } = operations.data;
  if (content.length === 0) {
    return <p className={styles.muted}>Nenhuma portabilidade solicitada.</p>;
  }

  return (
    <>
      <ul>
        {content.map((operation) => (
          <OperationHistoryItem key={operation.id} operation={operation} />
        ))}
      </ul>
      {pageInfo.totalPages > 1 && (
        <Pagination
          page={page}
          totalPages={pageInfo.totalPages}
          onChange={setPage}
          label="Páginas de portabilidades"
          disabled={operations.isPlaceholderData}
        />
      )}
    </>
  );
};

export const OperationsHistory = ({ beneficiaryId }: OperationsHistoryProps) => (
  <section className={styles.operationsHistory} aria-labelledby="operations-history-title">
    <h2 id="operations-history-title">Suas portabilidades</h2>
    <OperationsList beneficiaryId={beneficiaryId} />
  </section>
);
