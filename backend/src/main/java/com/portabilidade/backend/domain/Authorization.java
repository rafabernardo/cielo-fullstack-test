package com.portabilidade.backend.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// A beneficiary's opt-in, stored only once the Central has registered it.
@Entity
@Table(name = "authorizations")
public class Authorization {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String beneficiaryId;

	@Column(nullable = false)
	private String centralAuthorizationId;

	@Column(nullable = false)
	private Instant createdAt;

	protected Authorization() {
	}

	public Authorization(String beneficiaryId, String centralAuthorizationId) {
		this.id = UUID.randomUUID();
		this.beneficiaryId = beneficiaryId;
		this.centralAuthorizationId = centralAuthorizationId;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getBeneficiaryId() {
		return beneficiaryId;
	}

	public String getCentralAuthorizationId() {
		return centralAuthorizationId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
