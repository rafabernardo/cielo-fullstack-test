package com.portabilidade.backend.dto;

import java.util.HashSet;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CreatePortabilityOperationRequest(
		@NotBlank String beneficiaryId,
		@NotEmpty List<@NotBlank String> installmentIds,
		@NotNull @Valid DestinationAccountRequest destinationAccount) {

	@JsonIgnore
	@AssertTrue(message = "installmentIds must not contain duplicates")
	public boolean isInstallmentIdsUnique() {
		return installmentIds == null || new HashSet<>(installmentIds).size() == installmentIds.size();
	}

}
