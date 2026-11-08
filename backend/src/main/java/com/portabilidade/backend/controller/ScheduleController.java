package com.portabilidade.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.portabilidade.backend.dto.ScheduleResponse;
import com.portabilidade.backend.service.ScheduleService;

@RestController
@RequestMapping("/api/beneficiaries")
public class ScheduleController {

	private final ScheduleService scheduleService;

	public ScheduleController(ScheduleService scheduleService) {
		this.scheduleService = scheduleService;
	}

	@GetMapping("/{beneficiaryId}/schedule")
	public ScheduleResponse getSchedule(@PathVariable String beneficiaryId) {
		return ScheduleResponse.from(beneficiaryId, scheduleService.getSchedule(beneficiaryId));
	}

}
