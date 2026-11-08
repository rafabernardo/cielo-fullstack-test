package com.portabilidade.backend.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.portabilidade.backend.domain.Installment;
import com.portabilidade.backend.domain.InstallmentStatus;
import com.portabilidade.backend.exception.AuthorizationRequiredException;
import com.portabilidade.backend.integration.centralconvenios.CentralConveniosGateway;
import com.portabilidade.backend.integration.centralconvenios.CentralInstallment;
import com.portabilidade.backend.integration.centralconvenios.CentralInstallmentStatus;
import com.portabilidade.backend.repository.AuthorizationRepository;
import com.portabilidade.backend.repository.InstallmentLockRepository;

@Service
public class ScheduleService {

	private final AuthorizationRepository authorizationRepository;
	private final InstallmentLockRepository installmentLockRepository;
	private final CentralConveniosGateway centralConveniosGateway;

	public ScheduleService(AuthorizationRepository authorizationRepository,
			InstallmentLockRepository installmentLockRepository, CentralConveniosGateway centralConveniosGateway) {
		this.authorizationRepository = authorizationRepository;
		this.installmentLockRepository = installmentLockRepository;
		this.centralConveniosGateway = centralConveniosGateway;
	}

	public List<Installment> getSchedule(String beneficiaryId) {
		if (!authorizationRepository.existsByBeneficiaryId(beneficiaryId)) {
			throw new AuthorizationRequiredException();
		}
		var centralInstallments = centralConveniosGateway.getSchedule(beneficiaryId);
		var locked = Set.copyOf(installmentLockRepository.findLockedInstallmentIds(
				centralInstallments.stream().map(CentralInstallment::installmentId).toList()));
		return centralInstallments.stream()
				.map(installment -> toInstallment(installment, locked.contains(installment.installmentId())))
				.toList();
	}

	private static Installment toInstallment(CentralInstallment installment, boolean lockedByOperation) {
		return new Installment(installment.installmentId(), installment.agreementId(), installment.amount(),
				installment.deductionDate(), toStatus(installment.status()), lockedByOperation);
	}

	// Exhaustive on purpose: a new Central status fails compilation until it is mapped to our vocabulary.
	private static InstallmentStatus toStatus(CentralInstallmentStatus status) {
		return switch (status) {
			case ABERTA -> InstallmentStatus.OPEN;
		};
	}

}
