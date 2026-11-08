package com.portabilidade.backend.integration.centralconvenios;

import java.time.Duration;

/**
 * Outcome of checking a submitted operation at the Central. Sealed so callers handle every case exhaustively.
 */
public sealed interface CentralPortabilityResult {

	/** The Central has not decided yet (HTTP 204); ask again after {@code retryAfter}. */
	record Pending(Duration retryAfter) implements CentralPortabilityResult {
	}

	record Confirmed() implements CentralPortabilityResult {
	}

	record Rejected(String reason) implements CentralPortabilityResult {
	}

	/** The Central does not know the protocol (HTTP 404), e.g. it lost its in-memory state. */
	record NotFound() implements CentralPortabilityResult {
	}

}
