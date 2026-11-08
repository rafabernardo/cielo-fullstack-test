package com.portabilidade.backend.service;

import java.util.function.Consumer;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.portabilidade.backend.domain.PortabilityOperation;
import com.portabilidade.backend.domain.PortabilityOperationStatusChanged;
import com.portabilidade.backend.repository.InstallmentLockRepository;

/**
 * Applies a change to a managed operation inside the caller's transaction, together with the side effects every
 * status change needs: release the installments of an operation that no longer holds them, and announce the change
 * (delivered after commit). A change that keeps the status has no side effects.
 */
@Service
public class PortabilityOperationTransitionService {

	private final InstallmentLockRepository installmentLockRepository;
	private final ApplicationEventPublisher events;

	public PortabilityOperationTransitionService(InstallmentLockRepository installmentLockRepository,
			ApplicationEventPublisher events) {
		this.installmentLockRepository = installmentLockRepository;
		this.events = events;
	}

	public void apply(PortabilityOperation operation, Consumer<PortabilityOperation> change) {
		var statusBefore = operation.getStatus();
		change.accept(operation);
		if (operation.getStatus() == statusBefore) {
			return;
		}
		if (!operation.isHoldingInstallments()) {
			installmentLockRepository.deleteByOperationId(operation.getId());
		}
		events.publishEvent(new PortabilityOperationStatusChanged(operation.getId()));
	}

}
