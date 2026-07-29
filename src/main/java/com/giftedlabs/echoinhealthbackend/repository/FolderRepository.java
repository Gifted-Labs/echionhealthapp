package com.giftedlabs.echoinhealthbackend.repository;

import com.giftedlabs.echoinhealthbackend.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FolderRepository extends JpaRepository<Folder, String> {

    List<Folder> findByUserIdAndOrganizationIdAndParentFolderIsNull(String userId, String organizationId);

    List<Folder> findByUserIdAndOrganizationIdAndParentFolderId(String userId, String organizationId, String parentFolderId);

    Optional<Folder> findByIdAndUserIdAndOrganizationId(String id, String userId, String organizationId);

    boolean existsByUserIdAndOrganizationIdAndNameAndParentFolderId(
            String userId,
            String organizationId,
            String name,
            String parentFolderId);
}
