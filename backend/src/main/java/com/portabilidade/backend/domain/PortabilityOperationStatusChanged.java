package com.portabilidade.backend.domain;

import java.util.UUID;

// Published after an operation changes status, so interested parties (e.g. live subscribers) can react.
public record PortabilityOperationStatusChanged(UUID operationId) {
}
