package com.portabilidade.backend.exception;

import java.time.Duration;

public class CentralConveniosUnavailableException extends CentralConveniosException {

	public CentralConveniosUnavailableException(String message, Duration retryAfter) {
		super(message, retryAfter);
	}

}
