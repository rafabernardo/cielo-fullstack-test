package com.portabilidade.backend.exception;

import java.time.Duration;

/**
 * A failed call to the Central de Convênios. Carries how long to wait before trying again: the Central's
 * {@code Retry-After} when it sent one, otherwise the configured default.
 */
public abstract class CentralConveniosException extends RuntimeException {

	private final Duration retryAfter;

	protected CentralConveniosException(String message, Duration retryAfter) {
		super(message);
		this.retryAfter = retryAfter;
	}

	public Duration getRetryAfter() {
		return retryAfter;
	}

}
