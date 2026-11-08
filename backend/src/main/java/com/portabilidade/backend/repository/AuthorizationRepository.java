package com.portabilidade.backend.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.portabilidade.backend.domain.Authorization;

public interface AuthorizationRepository extends JpaRepository<Authorization, UUID> {

	boolean existsByBeneficiaryId(String beneficiaryId);

}
