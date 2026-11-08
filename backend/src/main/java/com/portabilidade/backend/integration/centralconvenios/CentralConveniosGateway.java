package com.portabilidade.backend.integration.centralconvenios;

import java.util.List;

public interface CentralConveniosGateway {

	/**
	 * Registers the beneficiary's opt-in at the Central and returns the Central's authorization id.
	 */
	String createAuthorization(String beneficiaryId);

	/**
	 * Returns the beneficiary's future deductions as known by the Central; empty when it has none.
	 */
	List<CentralInstallment> getSchedule(String beneficiaryId);

	/**
	 * Submits a portability operation; the Central decides asynchronously. Returns the Central's protocol.
	 */
	String submitPortability(CentralPortabilityRequest request);

	/**
	 * Checks the outcome of a submitted operation by its protocol.
	 */
	CentralPortabilityResult getPortabilityStatus(String protocol);

}
