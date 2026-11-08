import { screen } from '@testing-library/react';
import { expect, test } from 'vitest';
import { App } from './app';
import { renderWithProviders } from '@/testing/render';

test('shows the page title', () => {
  renderWithProviders(<App />);

  expect(screen.getByRole('heading', { name: 'Portabilidade de convênio' })).toBeInTheDocument();
});
