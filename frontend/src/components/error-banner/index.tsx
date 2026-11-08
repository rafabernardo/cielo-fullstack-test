import type { ReactNode } from 'react';
import styles from './error-banner.module.css';

type ErrorBannerProps = {
  children: ReactNode;
  // Shows a "Tentar novamente" button when the failed request is worth repeating.
  onRetry?: () => void;
};

export const ErrorBanner = ({ children, onRetry }: ErrorBannerProps) => (
  <div role="alert" className={styles.errorBanner}>
    <span>{children}</span>
    {onRetry && (
      <button type="button" className={styles.retry} onClick={onRetry}>
        Tentar novamente
      </button>
    )}
  </div>
);
