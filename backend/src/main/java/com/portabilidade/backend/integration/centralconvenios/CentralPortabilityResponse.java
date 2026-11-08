package com.portabilidade.backend.integration.centralconvenios;

import com.fasterxml.jackson.annotation.JsonProperty;

record CentralPortabilityResponse(@JsonProperty("protocolo") String protocol) {
}
