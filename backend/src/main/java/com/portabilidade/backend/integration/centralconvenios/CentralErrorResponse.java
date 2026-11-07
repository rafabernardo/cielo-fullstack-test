package com.portabilidade.backend.integration.centralconvenios;

import com.fasterxml.jackson.annotation.JsonProperty;

record CentralErrorResponse(@JsonProperty("erro") String error) {
}
