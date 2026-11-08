package com.portabilidade.backend.controller;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.portabilidade.backend.dto.PortabilityOperationResponse;
import com.portabilidade.backend.service.PortabilityOperationService;

import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("/api/beneficiaries")
public class BeneficiaryPortabilityOperationController {

	private final PortabilityOperationService portabilityOperationService;

	public BeneficiaryPortabilityOperationController(PortabilityOperationService portabilityOperationService) {
		this.portabilityOperationService = portabilityOperationService;
	}

	@GetMapping("/{beneficiaryId}/portability-operations")
	@ApiResponse(responseCode = "200", description = "The beneficiary's operations, newest first; sort is ignored")
	public PagedModel<PortabilityOperationResponse> list(@PathVariable String beneficiaryId,
			@ParameterObject Pageable pageable) {
		return new PagedModel<>(portabilityOperationService.listByBeneficiary(beneficiaryId, pageable)
				.map(PortabilityOperationResponse::from));
	}

}
