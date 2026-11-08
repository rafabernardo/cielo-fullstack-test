import { describe, expect, test } from 'vitest';
import { errorMessage, isRetryable } from './error-message';
import { ApiError } from '@/lib/api-client';

describe('errorMessage', () => {
  test('translates a known code', () => {
    const error = new ApiError(503, { code: 'CENTRAL_CONVENIOS_UNAVAILABLE', detail: 'Down' });

    expect(errorMessage(error)).toBe(
      'A Central de Convênios está indisponível no momento. Tente novamente em instantes.',
    );
  });

  test('falls back to the detail of an unknown code', () => {
    expect(errorMessage(new ApiError(405, { code: 'METHOD_NOT_ALLOWED', detail: 'Nope' }))).toBe(
      'Nope',
    );
  });

  test('falls back to a generic message without detail or for non-API errors', () => {
    expect(errorMessage(new ApiError(500, {}))).toBe('Algo deu errado. Tente novamente.');
    expect(errorMessage(new Error('boom'))).toBe('Algo deu errado. Tente novamente.');
  });

  test('explains a network failure', () => {
    expect(errorMessage(new ApiError(0, { code: 'NETWORK_ERROR' }))).toBe(
      'Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.',
    );
  });
});

describe('isRetryable', () => {
  test('is true for Central failures and network errors only', () => {
    expect(isRetryable(new ApiError(502, { code: 'CENTRAL_CONVENIOS_BAD_RESPONSE' }))).toBe(true);
    expect(isRetryable(new ApiError(503, { code: 'CENTRAL_CONVENIOS_UNAVAILABLE' }))).toBe(true);
    expect(isRetryable(new ApiError(0, { code: 'NETWORK_ERROR' }))).toBe(true);
    expect(isRetryable(new ApiError(422, { code: 'AUTHORIZATION_REQUIRED' }))).toBe(false);
    expect(isRetryable(new Error('boom'))).toBe(false);
  });
});
