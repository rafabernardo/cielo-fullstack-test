package com.portabilidade.backend.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.portabilidade.backend.domain.DestinationAccount;
import com.portabilidade.backend.domain.Installment;
import com.portabilidade.backend.domain.InstallmentLock;
import com.portabilidade.backend.domain.PortabilityOperation;
import com.portabilidade.backend.domain.PortabilityOperationStatus;
import com.portabilidade.backend.exception.CentralConveniosException;
import com.portabilidade.backend.exception.InstallmentInConfirmedOperationException;
import com.portabilidade.backend.exception.InstallmentInOperationException;
import com.portabilidade.backend.exception.InstallmentInPendingOperationException;
import com.portabilidade.backend.exception.InstallmentNotEligibleException;
import com.portabilidade.backend.exception.PortabilityOperationNotFoundException;
import com.portabilidade.backend.integration.centralconvenios.CentralConveniosGateway;
import com.portabilidade.backend.integration.centralconvenios.CentralDestinationAccount;
import com.portabilidade.backend.integration.centralconvenios.CentralPortabilityRequest;
import com.portabilidade.backend.repository.InstallmentLockRepository;
import com.portabilidade.backend.repository.PortabilityOperationRepository;

/**
 * Creates portability operations. An installment can be held by one active operation only, so a repeated request
 * is refused while the first one holds its installments. Remote calls never run inside a transaction: the operation
 * and its installment locks are committed first (so concurrent duplicates fail on the lock's unique constraint),
 * then submitted.
 */
@Service
public class PortabilityOperationService {

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

	public PortabilityOperation create(String beneficiaryId, List<String> installmentIds,
			DestinationAccount destinationAccount) {
		requireSelectable(beneficiaryId, installmentIds);
		PortabilityOperation operation;
		try {
			operation = transaction.execute(status -> lock(operationRepository.saveAndFlush(
					new PortabilityOperation(beneficiaryId, installmentIds, destinationAccount))));
		} catch (DataIntegrityViolationException e) {
			// A concurrent request took some of these installments first.
			throw buildConflict(installmentIds);
		}
		return submit(operation);
	}

	public PortabilityOperation get(UUID id) {
		return operationRepository.findById(id).orElseThrow(PortabilityOperationNotFoundException::new);
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
		if (installmentIds.stream().anyMatch(id -> schedule.get(id).lockedByOperation())) {
			throw buildConflict(installmentIds);
		}
	}

	// Lists the requested installments that are held, in request order, and the operations holding them. Confirmed
	// holders win over pending ones: those installments can never be ported again.
	private InstallmentInOperationException buildConflict(List<String> installmentIds) {
		Map<String, UUID> holders = installmentLockRepository.findByInstallmentIdIn(installmentIds).stream()
				.collect(Collectors.toMap(InstallmentLock::getInstallmentId, InstallmentLock::getOperationId));
		var confirmed = operationRepository.findAllById(Set.copyOf(holders.values())).stream()
				.filter(operation -> operation.getStatus() == PortabilityOperationStatus.CONFIRMED)
				.map(PortabilityOperation::getId)
				.collect(Collectors.toSet());
		var heldByConfirmed = installmentIds.stream()
				.filter(id -> holders.containsKey(id) && confirmed.contains(holders.get(id)))
				.toList();
		if (!heldByConfirmed.isEmpty()) {
			return new InstallmentInConfirmedOperationException(heldByConfirmed, holdersOf(heldByConfirmed, holders));
		}
		var held = installmentIds.stream().filter(holders::containsKey).toList();
		return new InstallmentInPendingOperationException(held, holdersOf(held, holders));
	}

	private static List<UUID> holdersOf(List<String> installmentIds, Map<String, UUID> holders) {
		return installmentIds.stream().map(holders::get).distinct().toList();
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
