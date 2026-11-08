package com.portabilidade.backend.dto;

import com.portabilidade.backend.domain.DestinationAccount;

public record DestinationAccountResponse(String bankCode, String branch, String accountNumber) {

	public static DestinationAccountResponse from(DestinationAccount account) {
		return new DestinationAccountResponse(account.getBankCode(), account.getBranch(), account.getAccountNumber());
	}

}
