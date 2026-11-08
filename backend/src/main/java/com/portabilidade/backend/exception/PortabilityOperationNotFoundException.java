package com.portabilidade.backend.exception;

public class PortabilityOperationNotFoundException extends RuntimeException {

	public PortabilityOperationNotFoundException() {
		super("Portability operation not found.");
	}

}
