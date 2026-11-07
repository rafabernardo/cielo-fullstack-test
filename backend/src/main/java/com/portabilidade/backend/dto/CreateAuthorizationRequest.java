package com.portabilidade.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAuthorizationRequest(@NotBlank String beneficiaryId) {
}
