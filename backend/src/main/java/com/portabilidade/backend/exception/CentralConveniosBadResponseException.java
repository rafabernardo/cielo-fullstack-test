package com.portabilidade.backend.exception;

import java.time.Duration;

public class CentralConveniosBadResponseException extends CentralConveniosException {

	public CentralConveniosBadResponseException(Duration retryAfter) {
		super("Central de Convênios returned an unexpected response.", retryAfter);
	}

}
