package com.giftedlabs.echoinhealthbackend.repository;

import com.giftedlabs.echoinhealthbackend.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FolderRepository extends JpaRepository<Folder, String> {

    List<Folder> findByUserIdAndParentFolderIsNull(String userId);

    List<Folder> findByUserIdAndParentFolderId(String userId, String parentFolderId);

    Optional<Folder> findByIdAndUserId(String id, String userId);

    boolean existsByUserIdAndNameAndParentFolderId(String userId, String name, String parentFolderId);
}
