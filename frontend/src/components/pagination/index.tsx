import styles from './pagination.module.css';

type PaginationProps = {
  // Zero-based, like the backend's `page`.
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
  label: string;
  disabled?: boolean;
};

export const Pagination = ({
  page,
  totalPages,
  onChange,
  label,
  disabled = false,
}: PaginationProps) => (
  <nav className={styles.pagination} aria-label={label}>
    <button type="button" disabled={disabled || page === 0} onClick={() => onChange(page - 1)}>
      Anterior
    </button>
    <span>
      Página {page + 1} de {totalPages}
    </span>
    <button
      type="button"
      disabled={disabled || page >= totalPages - 1}
      onClick={() => onChange(page + 1)}
    >
      Próxima
    </button>
  </nav>
);
