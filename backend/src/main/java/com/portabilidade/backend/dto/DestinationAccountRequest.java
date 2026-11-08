package com.portabilidade.backend.dto;

import com.portabilidade.backend.domain.DestinationAccount;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record DestinationAccountRequest(
		@NotNull @Pattern(regexp = "\\d{3}", message = "must have 3 digits") String bankCode,
		@NotNull @Pattern(regexp = "\\d{4}", message = "must have 4 digits") String branch,
		@NotNull @Pattern(regexp = "\\d{1,12}(-[0-9Xx])?", message = "must be digits with an optional check digit")
		String accountNumber) {

	public DestinationAccount toDomain() {
		return new DestinationAccount(bankCode, branch, accountNumber);
	}

}
