package com.portabilidade.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.portabilidade.backend.domain.PortabilityOperation;

public interface PortabilityOperationRepository extends JpaRepository<PortabilityOperation, UUID> {

	Optional<PortabilityOperation> findByIdempotencyKey(UUID idempotencyKey);

}
