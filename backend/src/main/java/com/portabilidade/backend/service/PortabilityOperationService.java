package com.portabilidade.backend.service;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.portabilidade.backend.domain.DestinationAccount;
import com.portabilidade.backend.domain.Installment;
import com.portabilidade.backend.domain.InstallmentLock;
import com.portabilidade.backend.domain.PortabilityOperation;
import com.portabilidade.backend.exception.CentralConveniosException;
import com.portabilidade.backend.exception.IdempotencyKeyReusedException;
import com.portabilidade.backend.exception.InstallmentInOperationException;
import com.portabilidade.backend.exception.InstallmentNotEligibleException;
import com.portabilidade.backend.exception.PortabilityOperationNotFoundException;
import com.portabilidade.backend.integration.centralconvenios.CentralConveniosGateway;
import com.portabilidade.backend.integration.centralconvenios.CentralDestinationAccount;
import com.portabilidade.backend.integration.centralconvenios.CentralPortabilityRequest;
import com.portabilidade.backend.repository.InstallmentLockRepository;
import com.portabilidade.backend.repository.PortabilityOperationRepository;

/**
 * Creates portability operations idempotently. Remote calls never run inside a transaction: the operation and its
 * installment locks are committed first (so concurrent duplicates fail on unique constraints), then submitted.
 */
@Service
public class PortabilityOperationService {

	/** The operation, and whether this request submitted it to the Central (new or retried) or just replayed it. */
	public record Creation(PortabilityOperation operation, boolean wasSubmitted) {
	}

	private static final Logger log = LoggerFactory.getLogger(PortabilityOperationService.class);

	private final PortabilityOperationRepository operationRepository;
	private final InstallmentLockRepository installmentLockRepository;
	private final ScheduleService scheduleService;
	private final CentralConveniosGateway centralConveniosGateway;
	private final PortabilityOperationTransitionService transitions;
	private final TransactionTemplate transaction;

	public PortabilityOperationService(PortabilityOperationRepository operationRepository,
			InstallmentLockRepository installmentLockRepository, ScheduleService scheduleService,
			CentralConveniosGateway centralConveniosGateway, PortabilityOperationTransitionService transitions,
			TransactionTemplate transaction) {
		this.operationRepository = operationRepository;
		this.installmentLockRepository = installmentLockRepository;
		this.scheduleService = scheduleService;
		this.centralConveniosGateway = centralConveniosGateway;
		this.transitions = transitions;
		this.transaction = transaction;
	}

	public Creation create(UUID idempotencyKey, String beneficiaryId, List<String> installmentIds,
			DestinationAccount destinationAccount) {
		var fingerprint = PortabilityOperation.fingerprintOf(beneficiaryId, installmentIds, destinationAccount);
		var existing = operationRepository.findByIdempotencyKey(idempotencyKey);
		if (existing.isPresent()) {
			return replay(existing.get(), fingerprint);
		}
		requireSelectable(beneficiaryId, installmentIds);
		PortabilityOperation operation;
		try {
			operation = transaction.execute(status -> lock(operationRepository.saveAndFlush(
					new PortabilityOperation(idempotencyKey, beneficiaryId, installmentIds, destinationAccount))));
		} catch (DataIntegrityViolationException e) {
			// A concurrent request with the same key won the insert: this one is a replay of it.
			var concurrent = operationRepository.findByIdempotencyKey(idempotencyKey);
			if (concurrent.isPresent()) {
				return replay(concurrent.get(), fingerprint);
			}
			throw new InstallmentInOperationException(
					installmentLockRepository.findLockedInstallmentIds(installmentIds));
		}
		return new Creation(submit(operation), true);
	}

	public PortabilityOperation get(UUID id) {
		return operationRepository.findById(id).orElseThrow(PortabilityOperationNotFoundException::new);
	}

	private Creation replay(PortabilityOperation operation, String fingerprint) {
		if (!operation.hasFingerprint(fingerprint)) {
			throw new IdempotencyKeyReusedException();
		}
		if (!operation.isFailed()) {
			return new Creation(operation, false);
		}
		return retry(operation.getId());
	}

	private Creation retry(UUID operationId) {
		PortabilityOperation operation;
		try {
			operation = transaction.execute(status -> {
				var failed = get(operationId);
				transitions.apply(failed, PortabilityOperation::retry);
				return lock(operationRepository.saveAndFlush(failed));
			});
		} catch (OptimisticLockingFailureException e) {
			// A concurrent retry of the same operation won: return its current state.
			return new Creation(get(operationId), false);
		} catch (DataIntegrityViolationException e) {
			throw new InstallmentInOperationException(
					installmentLockRepository.findLockedInstallmentIds(get(operationId).getInstallmentIds()));
		}
		return new Creation(submit(operation), true);
	}

	// Rejects ids missing from the schedule or not open (422) before ids taken by another operation (409).
	private void requireSelectable(String beneficiaryId, List<String> installmentIds) {
		var schedule = scheduleService.getSchedule(beneficiaryId).stream()
				.collect(Collectors.toMap(Installment::installmentId, Function.identity()));
		var notEligible = installmentIds.stream()
				.filter(id -> !schedule.containsKey(id) || !schedule.get(id).isOpen())
				.toList();
		if (!notEligible.isEmpty()) {
			throw new InstallmentNotEligibleException(notEligible);
		}
		var inOperation = installmentIds.stream().filter(id -> schedule.get(id).lockedByOperation()).toList();
		if (!inOperation.isEmpty()) {
			throw new InstallmentInOperationException(inOperation);
		}
	}

	private PortabilityOperation lock(PortabilityOperation operation) {
		installmentLockRepository.saveAllAndFlush(operation.getInstallmentIds().stream()
				.map(installmentId -> new InstallmentLock(installmentId, operation.getId()))
				.toList());
		return operation;
	}

	// ponytail: a crash between the insert and this call leaves a PENDING operation without protocol; harmless
	// with in-memory H2, a recovery job would be needed with a persistent database.
	private PortabilityOperation submit(PortabilityOperation operation) {
		String protocol;
		try {
			protocol = centralConveniosGateway.submitPortability(toCentralRequest(operation));
		} catch (CentralConveniosException e) {
			log.warn("Submission of portability operation {} failed: {}", operation.getId(),
					e.getClass().getSimpleName());
			transaction.executeWithoutResult(status -> transitions.apply(get(operation.getId()),
					PortabilityOperation::fail));
			throw e;
		}
		return transaction.execute(status -> {
			var submitted = get(operation.getId());
			submitted.markSubmitted(protocol);
			return submitted;
		});
	}

	private static CentralPortabilityRequest toCentralRequest(PortabilityOperation operation) {
		var account = operation.getDestinationAccount();
		return new CentralPortabilityRequest(operation.getBeneficiaryId(), operation.getInstallmentIds(),
				new CentralDestinationAccount(account.getBankCode(), account.getBranch(), account.getAccountNumber()));
	}

}
