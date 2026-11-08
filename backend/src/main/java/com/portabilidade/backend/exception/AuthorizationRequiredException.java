package com.portabilidade.backend.exception;

public class AuthorizationRequiredException extends RuntimeException {

	public AuthorizationRequiredException() {
		super("Beneficiary has no registered authorization.");
	}

}
