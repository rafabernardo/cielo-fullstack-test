package com.portabilidade.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.portabilidade.backend.domain.Installment;
import com.portabilidade.backend.domain.InstallmentStatus;
import com.portabilidade.backend.exception.AuthorizationRequiredException;
import com.portabilidade.backend.integration.centralconvenios.CentralConveniosGateway;
import com.portabilidade.backend.integration.centralconvenios.CentralInstallment;
import com.portabilidade.backend.integration.centralconvenios.CentralInstallmentStatus;
import com.portabilidade.backend.repository.AuthorizationRepository;

@Service
public class ScheduleService {

	private final AuthorizationRepository authorizationRepository;
	private final CentralConveniosGateway centralConveniosGateway;

	public ScheduleService(AuthorizationRepository authorizationRepository,
			CentralConveniosGateway centralConveniosGateway) {
		this.authorizationRepository = authorizationRepository;
		this.centralConveniosGateway = centralConveniosGateway;
	}

	public List<Installment> getSchedule(String beneficiaryId) {
		if (!authorizationRepository.existsByBeneficiaryId(beneficiaryId)) {
			throw new AuthorizationRequiredException();
		}
		return centralConveniosGateway.getSchedule(beneficiaryId).stream().map(ScheduleService::toInstallment).toList();
	}

	private static Installment toInstallment(CentralInstallment installment) {
		return new Installment(installment.installmentId(), installment.agreementId(), installment.amount(),
				installment.deductionDate(), toStatus(installment.status()));
	}

	// Exhaustive on purpose: a new Central status fails compilation until it is mapped to our vocabulary.
	private static InstallmentStatus toStatus(CentralInstallmentStatus status) {
		return switch (status) {
			case ABERTA -> InstallmentStatus.OPEN;
		};
	}

}
