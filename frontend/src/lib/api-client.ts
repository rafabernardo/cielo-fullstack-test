import { envs } from '@/config/env';

// RFC 9457 problem returned by the backend for every error (see backend/docs/API.md).
export type Problem = {
  code?: string;
  detail?: string;
  errors?: Record<string, string>;
  installmentIds?: string[];
  operationIds?: string[];
};

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly detail?: string;
  readonly errors?: Record<string, string>;
  readonly installmentIds?: string[];
  readonly operationIds?: string[];

  // status 0 means the request never got an answer (backend down, no network).
  constructor(status: number, problem: Problem) {
    super(problem.detail ?? `Request failed with status ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.code = problem.code ?? 'UNKNOWN_ERROR';
    this.detail = problem.detail;
    this.errors = problem.errors;
    this.installmentIds = problem.installmentIds;
    this.operationIds = problem.operationIds;
  }
}

const request = async <T>(path: string, init?: RequestInit): Promise<T> => {
  let response: Response;
  try {
    // Absolute URL: fetch outside the browser (tests) rejects relative ones.
    response = await fetch(new URL(`${envs.VITE_API_URL}${path}`, window.location.origin), init);
  } catch (error) {
    // A cancelled request is not a failure: let the AbortError through for the caller to ignore.
    if (init?.signal?.aborted) throw error;
    throw new ApiError(0, { code: 'NETWORK_ERROR' });
  }
  if (!response.ok) {
    const problem: Problem = await response.json().catch(() => ({}));
    // The dev proxy answers 502/504 without a problem body when the backend is down.
    const backendUnreachable =
      !problem.code && (response.status === 502 || response.status === 504);
    throw new ApiError(response.status, backendUnreachable ? { code: 'NETWORK_ERROR' } : problem);
  }
  return response.json();
};

// The context TanStack Query gives each query, passed whole: reading its `signal` is what lets the
// query abort the read once nothing observes it.
export type QueryContext = { readonly signal: AbortSignal };

// Cancels obsolete reads, but only from the next tick: TanStack Query aborts a read whose signal
// was read as soon as its last observer unmounts, which StrictMode's dev remount does right away,
// so the read would be aborted and sent again. Not reading the signal before then lets the
// remounted observer reuse the read in flight.
const deferredSignal = (context: QueryContext) => {
  const controller = new AbortController();
  setTimeout(() => {
    const { signal } = context;
    if (signal.aborted) controller.abort();
    else signal.addEventListener('abort', () => controller.abort(), { once: true });
  });
  return controller.signal;
};

export const apiGet = <T>(path: string, context?: QueryContext) =>
  request<T>(path, { signal: context && deferredSignal(context) });

export const apiPost = <T>(path: string, body: unknown) =>
  request<T>(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
