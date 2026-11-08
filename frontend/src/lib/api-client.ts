// RFC 7807 problem returned by the backend for every error (see backend/docs/API.md).
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
    response = await fetch(new URL(path, window.location.origin), init);
  } catch {
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

export const apiGet = <T>(path: string) => request<T>(path);

export const apiPost = <T>(path: string, body: unknown) =>
  request<T>(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
