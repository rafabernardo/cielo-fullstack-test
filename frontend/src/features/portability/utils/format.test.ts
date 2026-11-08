import { expect, test } from 'vitest';
import { formatCurrency, formatDate } from './format';

test('formats money as BRL', () => {
  // Intl separates the symbol with a non-breaking space.
  expect(formatCurrency(150)).toBe('R$ 150,00');
  expect(formatCurrency(1234.5)).toBe('R$ 1.234,50');
});

test('formats an ISO date as dd/mm/aaaa without shifting the day', () => {
  expect(formatDate('2026-01-10')).toBe('10/01/2026');
});
