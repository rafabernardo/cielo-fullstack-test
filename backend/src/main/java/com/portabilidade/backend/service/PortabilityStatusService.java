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
	private final CentralConveniosGateway centralConveniosGateway;
	private final PortabilityOperationTransitionService transitions;
	private final TransactionTemplate transaction;

	public PortabilityStatusService(PortabilityOperationRepository operationRepository,
			CentralConveniosGateway centralConveniosGateway, PortabilityOperationTransitionService transitions,
			TransactionTemplate transaction) {
		this.operationRepository = operationRepository;
		this.centralConveniosGateway = centralConveniosGateway;
		this.transitions = transitions;
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
			log.warn("Could not check portability operation {} (protocol {}): {}; stays PENDING", operation.getId(),
					operation.getProtocol(), e.getClass().getSimpleName());
			transition(operation, pendingOperation -> pendingOperation.checkAgainIn(e.getRetryAfter()));
			return;
		}
		switch (result) {
			case CentralPortabilityResult.Pending pending ->
					transition(operation, pendingOperation -> pendingOperation.checkAgainIn(pending.retryAfter()));
			case CentralPortabilityResult.Confirmed confirmed -> transition(operation, PortabilityOperation::confirm);
			case CentralPortabilityResult.Rejected rejected ->
					transition(operation, pendingOperation -> pendingOperation.reject(rejected.reason()));
			case CentralPortabilityResult.NotFound notFound -> {
				log.warn("Central de Convênios no longer knows portability operation {} (protocol {})",
						operation.getId(), operation.getProtocol());
				transition(operation, PortabilityOperation::fail);
			}
		}
	}

	// Skips a stale result: the operation may have left PENDING meanwhile.
	private void transition(PortabilityOperation checked, Consumer<PortabilityOperation> change) {
		try {
			transaction.executeWithoutResult(status -> {
				var current = operationRepository.findById(checked.getId()).orElseThrow();
				if (current.isPending()) {
					transitions.apply(current, change);
				}
			});
		} catch (OptimisticLockingFailureException e) {
			log.info("Portability operation {} changed concurrently; it will be checked again", checked.getId());
		}
	}

}
