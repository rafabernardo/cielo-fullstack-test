import { ApiError } from '@/lib/api-client';

const MESSAGES: Record<string, string> = {
  VALIDATION_ERROR: 'Alguns dados estão inválidos. Confira os campos destacados.',
  AUTHORIZATION_REQUIRED: 'O beneficiário ainda não autorizou a consulta (opt-in).',
  INSTALLMENT_NOT_ELIGIBLE:
    'Algumas parcelas não estão mais disponíveis para portabilidade. Atualize a agenda.',
  INSTALLMENT_IN_PENDING_OPERATION: 'Já existe uma solicitação em andamento para essas parcelas.',
  INSTALLMENT_IN_CONFIRMED_OPERATION:
    'Essas parcelas já foram portadas em uma operação confirmada.',
  PORTABILITY_OPERATION_NOT_FOUND: 'Operação de portabilidade não encontrada.',
  CENTRAL_CONVENIOS_BAD_RESPONSE:
    'A Central de Convênios respondeu de forma inesperada. Tente novamente.',
  CENTRAL_CONVENIOS_UNAVAILABLE:
    'A Central de Convênios está indisponível no momento. Tente novamente em instantes.',
  INTERNAL_ERROR: 'Erro interno no servidor. Tente novamente.',
  NETWORK_ERROR: 'Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.',
};

const GENERIC = 'Algo deu errado. Tente novamente.';

export const errorMessage = (error: unknown): string =>
  error instanceof ApiError ? (MESSAGES[error.code] ?? error.detail ?? GENERIC) : GENERIC;

// Failures worth a "Tentar novamente" button: the same request may succeed later.
export const isRetryable = (error: unknown): boolean =>
  error instanceof ApiError &&
  (error.status === 502 || error.status === 503 || error.code === 'NETWORK_ERROR');
