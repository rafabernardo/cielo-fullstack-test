package com.portabilidade.backend.dto;

public record AuthorizationStatusResponse(String beneficiaryId, boolean authorized) {
}
