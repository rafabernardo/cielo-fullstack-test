package com.portabilidade.backend.service;

import java.time.Instant;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.portabilidade.backend.domain.PortabilityOperation;
import com.portabilidade.backend.domain.PortabilityOperationStatus;
import com.portabilidade.backend.exception.CentralConveniosException;
import com.portabilidade.backend.integration.centralconvenios.CentralConveniosGateway;
import com.portabilidade.backend.integration.centralconvenios.CentralPortabilityResult;
import com.portabilidade.backend.repository.InstallmentLockRepository;
import com.portabilidade.backend.repository.PortabilityOperationRepository;

/**
 * Follows submitted operations at the Central until they are confirmed or rejected, asking about each one again
 * only when the Central's Retry-After says so. Each operation is checked outside any transaction and updated in
 * its own short one, so one failure never blocks the others.
 */
@Service
public class PortabilityStatusService {

	private static final Logger log = LoggerFactory.getLogger(PortabilityStatusService.class);

	private final PortabilityOperationRepository operationRepository;
	private final InstallmentLockRepository installmentLockRepository;
	private final CentralConveniosGateway centralConveniosGateway;
	private final TransactionTemplate transaction;

	public PortabilityStatusService(PortabilityOperationRepository operationRepository,
			InstallmentLockRepository installmentLockRepository, CentralConveniosGateway centralConveniosGateway,
			TransactionTemplate transaction) {
		this.operationRepository = operationRepository;
		this.installmentLockRepository = installmentLockRepository;
		this.centralConveniosGateway = centralConveniosGateway;
		this.transaction = transaction;
	}

	// ponytail: checks due operations sequentially each tick; page or parallelize if volume grows.
	@Scheduled(fixedDelayString = "${portability.status-poll-tick}")
	public void refreshPendingOperations() {
		var due = operationRepository.findAllByStatusAndNextCheckAtLessThanEqual(PortabilityOperationStatus.PENDING,
				Instant.now());
		for (var operation : due) {
			try {
				refresh(operation);
			} catch (RuntimeException e) {
				log.error("Unexpected error refreshing portability operation {}", operation.getId(), e);
			}
		}
	}

	private void refresh(PortabilityOperation operation) {
		CentralPortabilityResult result;
		try {
			result = centralConveniosGateway.getPortabilityStatus(operation.getProtocol());
		} catch (CentralConveniosException e) {
			// Already logged by the gateway; stays PENDING and is asked again when the Central allows.
			transition(operation, pendingOperation -> pendingOperation.checkAgainIn(e.getRetryAfter()), false);
			return;
		}
		switch (result) {
			case CentralPortabilityResult.Pending pending -> transition(operation,
					pendingOperation -> pendingOperation.checkAgainIn(pending.retryAfter()), false);
			case CentralPortabilityResult.Confirmed confirmed ->
					transition(operation, PortabilityOperation::confirm, false);
			case CentralPortabilityResult.Rejected rejected ->
					transition(operation, pendingOperation -> pendingOperation.reject(rejected.reason()), true);
			case CentralPortabilityResult.NotFound notFound -> {
				log.warn("Central de Convênios no longer knows portability operation {}", operation.getId());
				transition(operation, PortabilityOperation::fail, true);
			}
		}
	}

	private void transition(PortabilityOperation checked, Consumer<PortabilityOperation> change,
			boolean releaseInstallments) {
		try {
			transaction.executeWithoutResult(status -> {
				var current = operationRepository.findById(checked.getId()).orElseThrow();
				if (!isStillTheCheckedSubmission(current, checked.getProtocol())) {
					return;
				}
				change.accept(current);
				if (releaseInstallments) {
					installmentLockRepository.deleteByOperationId(current.getId());
				}
			});
		} catch (OptimisticLockingFailureException e) {
			log.info("Portability operation {} changed concurrently; it will be checked again", checked.getId());
		}
	}

	// The result is stale if the operation moved on meanwhile (e.g. failed and was resubmitted with a new protocol).
	private static boolean isStillTheCheckedSubmission(PortabilityOperation current, String checkedProtocol) {
		return current.isPending() && checkedProtocol.equals(current.getProtocol());
	}

}
