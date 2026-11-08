package com.portabilidade.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.portabilidade.backend.domain.InstallmentLock;

public interface InstallmentLockRepository extends JpaRepository<InstallmentLock, UUID> {

	@Query("select lock.installmentId from InstallmentLock lock where lock.installmentId in :installmentIds")
	List<String> findLockedInstallmentIds(Collection<String> installmentIds);

	List<InstallmentLock> findByInstallmentIdIn(Collection<String> installmentIds);

	void deleteByOperationId(UUID operationId);

}
