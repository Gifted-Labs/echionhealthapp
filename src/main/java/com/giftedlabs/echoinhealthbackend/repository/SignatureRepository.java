package com.giftedlabs.echoinhealthbackend.repository;

import com.giftedlabs.echoinhealthbackend.entity.Signature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SignatureRepository extends JpaRepository<Signature, String> {
    List<Signature> findByUserIdAndOrganizationIdOrderByCreatedAtDesc(String userId, String organizationId);

    Optional<Signature> findByIdAndUserIdAndOrganizationId(String id, String userId, String organizationId);

    Optional<Signature> findByIdAndOrganizationId(String id, String organizationId);

    Optional<Signature> findByUserIdAndOrganizationIdAndIsDefaultTrue(String userId, String organizationId);

    List<Signature> findByUserIdAndOrganizationIdAndIdNotOrderByCreatedAtDesc(String userId, String organizationId, String id);
}
