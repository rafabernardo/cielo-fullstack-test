package com.portabilidade.backend.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

// A request to port installments to a new creditor; the Central confirms or rejects it asynchronously.
@Entity
@Table(name = "portability_operations")
public class PortabilityOperation {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true)
	private UUID idempotencyKey;

	@Column(nullable = false)
	private String requestFingerprint;

	@Column(nullable = false)
	private String beneficiaryId;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "portability_operation_installments", joinColumns = @JoinColumn(name = "operation_id"))
	@OrderColumn(name = "position")
	@Column(name = "installment_id", nullable = false)
	private List<String> installmentIds = new ArrayList<>();

	@Embedded
	private DestinationAccount destinationAccount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PortabilityOperationStatus status;

	private String protocol;

	// When the Central should be asked again about this operation; follows the Central's Retry-After.
	private Instant nextCheckAt;

	private String rejectionReason;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	@Version
	private Long version;

	protected PortabilityOperation() {
	}

	public PortabilityOperation(UUID idempotencyKey, String beneficiaryId, List<String> installmentIds,
			DestinationAccount destinationAccount) {
		this.id = UUID.randomUUID();
		this.idempotencyKey = idempotencyKey;
		this.requestFingerprint = fingerprintOf(beneficiaryId, installmentIds, destinationAccount);
		this.beneficiaryId = beneficiaryId;
		this.installmentIds = new ArrayList<>(installmentIds);
		this.destinationAccount = destinationAccount;
		this.status = PortabilityOperationStatus.PENDING;
		this.createdAt = Instant.now();
		this.updatedAt = createdAt;
	}

	/**
	 * Identifies the intent of a request regardless of installment order, to tell an idempotent replay from a key
	 * reused with a different payload.
	 */
	public static String fingerprintOf(String beneficiaryId, List<String> installmentIds,
			DestinationAccount account) {
		var normalized = String.join("|", beneficiaryId, String.join(",", installmentIds.stream().sorted().toList()),
				account.getBankCode(), account.getBranch(), account.getAccountNumber());
		try {
			var digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is required by every JVM", e);
		}
	}

	public boolean hasFingerprint(String fingerprint) {
		return requestFingerprint.equals(fingerprint);
	}

	public void markSubmitted(String protocol) {
		requirePending();
		if (this.protocol != null) {
			throw new IllegalStateException("Operation " + id + " was already submitted");
		}
		this.protocol = protocol;
		this.nextCheckAt = Instant.now();
		touch();
	}

	public void checkAgainIn(Duration delay) {
		requirePending();
		this.nextCheckAt = Instant.now().plus(delay);
	}

	public void confirm() {
		requirePending();
		this.status = PortabilityOperationStatus.CONFIRMED;
		this.nextCheckAt = null;
		touch();
	}

	public void reject(String reason) {
		requirePending();
		this.status = PortabilityOperationStatus.REJECTED;
		this.rejectionReason = reason;
		this.nextCheckAt = null;
		touch();
	}

	public void fail() {
		requirePending();
		this.status = PortabilityOperationStatus.FAILED;
		this.nextCheckAt = null;
		touch();
	}

	public void retry() {
		if (status != PortabilityOperationStatus.FAILED) {
			throw new IllegalStateException("Only a failed operation can be retried; operation " + id + " is "
					+ status);
		}
		this.status = PortabilityOperationStatus.PENDING;
		this.protocol = null;
		this.nextCheckAt = null;
		touch();
	}

	public boolean isPending() {
		return status == PortabilityOperationStatus.PENDING;
	}

	/** Seconds a client should wait before reading this operation again; at least 1. */
	public long secondsUntilNextCheck() {
		if (nextCheckAt == null) {
			return 1;
		}
		var millis = Duration.between(Instant.now(), nextCheckAt).toMillis();
		return Math.max(1, (millis + 999) / 1000);
	}

	/** Whether this operation keeps its installments locked: while pending, and for good once confirmed. */
	public boolean isHoldingInstallments() {
		return status == PortabilityOperationStatus.PENDING || status == PortabilityOperationStatus.CONFIRMED;
	}

	/** Whether it is still waiting on the submission identified by {@code protocol} (not resubmitted meanwhile). */
	public boolean isPendingWith(String protocol) {
		return isPending() && protocol != null && protocol.equals(this.protocol);
	}

	public boolean isFailed() {
		return status == PortabilityOperationStatus.FAILED;
	}

	private void requirePending() {
		if (status != PortabilityOperationStatus.PENDING) {
			throw new IllegalStateException("Operation " + id + " is " + status + ", expected PENDING");
		}
	}

	private void touch() {
		this.updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getBeneficiaryId() {
		return beneficiaryId;
	}

	public List<String> getInstallmentIds() {
		return Collections.unmodifiableList(installmentIds);
	}

	public DestinationAccount getDestinationAccount() {
		return destinationAccount;
	}

	public PortabilityOperationStatus getStatus() {
		return status;
	}

	public String getProtocol() {
		return protocol;
	}

	public String getRejectionReason() {
		return rejectionReason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
