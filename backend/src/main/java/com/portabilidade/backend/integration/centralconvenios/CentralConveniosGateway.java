package com.portabilidade.backend.integration.centralconvenios;

public interface CentralConveniosGateway {

	/**
	 * Registers the beneficiary's opt-in at the Central and returns the Central's authorization id.
	 */
	String createAuthorization(String beneficiaryId);

}
