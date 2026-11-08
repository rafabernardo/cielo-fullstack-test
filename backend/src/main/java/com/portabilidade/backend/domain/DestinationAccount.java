package com.portabilidade.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class DestinationAccount {

	@Column(nullable = false)
	private String bankCode;

	@Column(nullable = false)
	private String branch;

	@Column(nullable = false)
	private String accountNumber;

	protected DestinationAccount() {
	}

	public DestinationAccount(String bankCode, String branch, String accountNumber) {
		this.bankCode = bankCode;
		this.branch = branch;
		this.accountNumber = accountNumber;
	}

	public String getBankCode() {
		return bankCode;
	}

	public String getBranch() {
		return branch;
	}

	public String getAccountNumber() {
		return accountNumber;
	}

}
