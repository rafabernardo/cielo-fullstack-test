import { QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { createQueryClient } from '@/lib/query-client';

// A fresh cache per test so no state leaks between tests.
export const renderWithProviders = (ui: ReactElement) =>
  render(<QueryClientProvider client={createQueryClient()}>{ui}</QueryClientProvider>);
