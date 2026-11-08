package com.portabilidade.backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.portabilidade.backend.domain.PortabilityOperation;
import com.portabilidade.backend.domain.PortabilityOperationStatus;

public interface PortabilityOperationRepository extends JpaRepository<PortabilityOperation, UUID> {

	List<PortabilityOperation> findAllByStatusAndNextCheckAtLessThanEqual(PortabilityOperationStatus status,
			Instant now);

}
