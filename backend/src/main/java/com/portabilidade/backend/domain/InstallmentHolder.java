package com.portabilidade.backend.domain;

import java.util.UUID;

// The portability operation holding an installment: PENDING while in progress, CONFIRMED once ported.
public record InstallmentHolder(UUID operationId, PortabilityOperationStatus status) {
}
