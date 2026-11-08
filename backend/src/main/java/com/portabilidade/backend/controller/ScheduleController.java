package com.portabilidade.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.portabilidade.backend.dto.ScheduleResponse;
import com.portabilidade.backend.service.ScheduleService;

import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("/api/beneficiaries")
public class ScheduleController {

	private final ScheduleService scheduleService;

	public ScheduleController(ScheduleService scheduleService) {
		this.scheduleService = scheduleService;
	}

	@GetMapping("/{beneficiaryId}/schedule")
	@ApiResponse(responseCode = "200", description = "The beneficiary's installments and which can be selected")
	@ApiResponse(responseCode = "422", description = "AUTHORIZATION_REQUIRED: beneficiary has no registered authorization")
	@ApiResponse(responseCode = "502", description = "CENTRAL_CONVENIOS_BAD_RESPONSE: Central answered unexpectedly")
	@ApiResponse(responseCode = "503", description = "CENTRAL_CONVENIOS_UNAVAILABLE: Central unreachable or timed out")
	public ScheduleResponse getSchedule(@PathVariable String beneficiaryId) {
		return ScheduleResponse.from(beneficiaryId, scheduleService.getSchedule(beneficiaryId));
	}

}
