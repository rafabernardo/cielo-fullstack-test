package com.portabilidade.backend.controller;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.portabilidade.backend.domain.DestinationAccount;
import com.portabilidade.backend.domain.PortabilityOperation;
import com.portabilidade.backend.repository.PortabilityOperationRepository;

// Lists a beneficiary's operations; they are stored directly, since listing never involves the Central.
@SpringBootTest
@AutoConfigureMockMvc
class BeneficiaryPortabilityOperationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PortabilityOperationRepository operationRepository;

	// Other test classes share this Spring context and database.
	@AfterEach
	void cleanDatabase() {
		operationRepository.deleteAll();
	}

	@Test
	void returnsEmptyPageForBeneficiaryWithoutOperationsOrAuthorization() throws Exception {
		var result = mockMvc.perform(get("/api/beneficiaries/BEN001/portability-operations"));

		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty())
				.andExpect(jsonPath("$.page.totalElements").value(0));
	}

	@Test
	void listsOperationsNewestFirstWithTheirCurrentStatus() throws Exception {
		var oldest = store("BEN001", "P001", PortabilityOperation::confirm);
		var middle = store("BEN001", "P002", operation -> operation.reject("Margem insuficiente"));
		var newest = store("BEN001", "P003", operation -> { });

		var result = mockMvc.perform(get("/api/beneficiaries/BEN001/portability-operations"));

		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].id").value(contains(
						newest.getId().toString(), middle.getId().toString(), oldest.getId().toString())))
				.andExpect(jsonPath("$.content[*].status").value(contains("PENDING", "REJECTED", "CONFIRMED")))
				.andExpect(jsonPath("$.content[1].rejectionReason").value("Margem insuficiente"))
				.andExpect(jsonPath("$.content[0].installmentIds").value(contains("P003")))
				.andExpect(jsonPath("$.content[0].destinationAccount.bankCode").value("001"));
	}

	@Test
	void ignoresOperationsOfOtherBeneficiaries() throws Exception {
		var own = store("BEN001", "P001", operation -> { });
		store("BEN002", "P010", operation -> { });

		var result = mockMvc.perform(get("/api/beneficiaries/BEN001/portability-operations"));

		result.andExpect(jsonPath("$.content[*].id").value(contains(own.getId().toString())));
	}

	@Test
	void pagesFiveOperationsAtATimeByDefault() throws Exception {
		var ids = List.of("P001", "P002", "P003", "P004", "P005", "P006").stream()
				.map(installment -> store("BEN001", installment, operation -> { }))
				.toList();

		var firstPage = mockMvc.perform(get("/api/beneficiaries/BEN001/portability-operations"));
		var secondPage = mockMvc.perform(get("/api/beneficiaries/BEN001/portability-operations?page=1"));

		firstPage.andExpect(jsonPath("$.content.length()").value(5))
				.andExpect(jsonPath("$.page.size").value(5))
				.andExpect(jsonPath("$.page.number").value(0))
				.andExpect(jsonPath("$.page.totalElements").value(6))
				.andExpect(jsonPath("$.page.totalPages").value(2));
		secondPage.andExpect(jsonPath("$.content[*].id").value(contains(ids.getFirst().getId().toString())))
				.andExpect(jsonPath("$.page.number").value(1));
	}

	@Test
	void capsPageSizeAtTwenty() throws Exception {
		var result = mockMvc.perform(get("/api/beneficiaries/BEN001/portability-operations?size=100"));

		result.andExpect(status().isOk()).andExpect(jsonPath("$.page.size").value(20));
	}

	private PortabilityOperation store(String beneficiaryId, String installmentId,
			Consumer<PortabilityOperation> transition) {
		var operation = new PortabilityOperation(beneficiaryId, List.of(installmentId),
				new DestinationAccount("001", "1234", "123456-7"));
		transition.accept(operation);
		var stored = operationRepository.save(operation);
		pauseSoNextOperationIsNewer();
		return stored;
	}

	// createdAt comes from the clock; a short pause keeps the expected order unambiguous.
	private static void pauseSoNextOperationIsNewer() {
		try {
			Thread.sleep(2);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
