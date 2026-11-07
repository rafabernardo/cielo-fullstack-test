package com.portabilidade.backend.exception;

public class CentralConveniosBadResponseException extends RuntimeException {

	public CentralConveniosBadResponseException() {
		super("Central de Convênios returned an unexpected response.");
	}

}
