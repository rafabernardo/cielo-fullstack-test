package com.portabilidade.backend.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.portabilidade.backend.domain.Installment;
import com.portabilidade.backend.domain.InstallmentHolder;
import com.portabilidade.backend.domain.InstallmentLock;
import com.portabilidade.backend.domain.InstallmentStatus;
import com.portabilidade.backend.domain.PortabilityOperation;
import com.portabilidade.backend.exception.AuthorizationRequiredException;
import com.portabilidade.backend.integration.centralconvenios.CentralConveniosGateway;
import com.portabilidade.backend.integration.centralconvenios.CentralInstallment;
import com.portabilidade.backend.integration.centralconvenios.CentralInstallmentStatus;
import com.portabilidade.backend.repository.AuthorizationRepository;
import com.portabilidade.backend.repository.InstallmentLockRepository;
import com.portabilidade.backend.repository.PortabilityOperationRepository;

@Service
public class ScheduleService {

	private final AuthorizationRepository authorizationRepository;
	private final InstallmentLockRepository installmentLockRepository;
	private final PortabilityOperationRepository operationRepository;
	private final CentralConveniosGateway centralConveniosGateway;

	public ScheduleService(AuthorizationRepository authorizationRepository,
			InstallmentLockRepository installmentLockRepository, PortabilityOperationRepository operationRepository,
			CentralConveniosGateway centralConveniosGateway) {
		this.authorizationRepository = authorizationRepository;
		this.installmentLockRepository = installmentLockRepository;
		this.operationRepository = operationRepository;
		this.centralConveniosGateway = centralConveniosGateway;
	}

	public List<Installment> getSchedule(String beneficiaryId) {
		if (!authorizationRepository.existsByBeneficiaryId(beneficiaryId)) {
			throw new AuthorizationRequiredException();
		}
		var centralInstallments = centralConveniosGateway.getSchedule(beneficiaryId);
		var holders = holdersOf(centralInstallments.stream().map(CentralInstallment::installmentId).toList());
		return centralInstallments.stream()
				.map(installment -> toInstallment(installment, holders.get(installment.installmentId())))
				.toList();
	}

	// Installment id -> operation holding it. An operation that stopped holding between the two reads is left out.
	private Map<String, InstallmentHolder> holdersOf(List<String> installmentIds) {
		var locks = installmentLockRepository.findByInstallmentIdIn(installmentIds);
		var operations = operationRepository.findAllById(locks.stream().map(InstallmentLock::getOperationId).toList())
				.stream()
				.filter(PortabilityOperation::isHoldingInstallments)
				.collect(Collectors.toMap(PortabilityOperation::getId, Function.identity()));
		return locks.stream()
				.filter(lock -> operations.containsKey(lock.getOperationId()))
				.collect(Collectors.toMap(InstallmentLock::getInstallmentId,
						lock -> holderOf(operations.get(lock.getOperationId()))));
	}

	private static InstallmentHolder holderOf(PortabilityOperation operation) {
		return new InstallmentHolder(operation.getId(), operation.getStatus());
	}

	private static Installment toInstallment(CentralInstallment installment, InstallmentHolder heldBy) {
		return new Installment(installment.installmentId(), installment.agreementId(), installment.amount(),
				installment.deductionDate(), toStatus(installment.status()), heldBy);
	}

	// Exhaustive on purpose: a new Central status fails compilation until it is mapped to our vocabulary.
	private static InstallmentStatus toStatus(CentralInstallmentStatus status) {
		return switch (status) {
			case ABERTA -> InstallmentStatus.OPEN;
		};
	}

}
