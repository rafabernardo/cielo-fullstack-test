// Shapes of backend/docs/API.md. Dates are yyyy-MM-dd, timestamps ISO-8601 instants, money a decimal number.

export type Authorization = {
  id: string;
  beneficiaryId: string;
  centralAuthorizationId: string;
  createdAt: string;
};

export type Installment = {
  installmentId: string;
  agreementId: string;
  amount: number;
  deductionDate: string;
  status: 'OPEN';
  eligible: boolean;
  // The operation holding it, or null when free.
  heldBy: { operationId: string; status: 'PENDING' | 'CONFIRMED' } | null;
};

export type Schedule = {
  beneficiaryId: string;
  installments: Installment[];
};

export type DestinationAccount = {
  bankCode: string;
  branch: string;
  accountNumber: string;
};

export type OperationStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'FAILED';

export type PortabilityOperation = {
  id: string;
  beneficiaryId: string;
  installmentIds: string[];
  destinationAccount: DestinationAccount;
  status: OperationStatus;
  rejectionReason: string | null;
  createdAt: string;
  updatedAt: string;
};

export type Page<T> = {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
};
