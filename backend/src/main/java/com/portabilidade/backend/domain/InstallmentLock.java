package com.portabilidade.backend.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Marks an installment as taken by an active (pending or confirmed) operation. The unique installment id is what
 * prevents two operations from porting the same installment, even under concurrency.
 */
@Entity
@Table(name = "installment_locks")
public class InstallmentLock {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true)
	private String installmentId;

	@Column(nullable = false)
	private UUID operationId;

	protected InstallmentLock() {
	}

	public InstallmentLock(String installmentId, UUID operationId) {
		this.id = UUID.randomUUID();
		this.installmentId = installmentId;
		this.operationId = operationId;
	}

	public String getInstallmentId() {
		return installmentId;
	}

	public UUID getOperationId() {
		return operationId;
	}

}
