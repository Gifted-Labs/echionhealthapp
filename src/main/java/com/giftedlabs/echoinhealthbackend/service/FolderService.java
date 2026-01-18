package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.vault.CreateFolderRequest;
import com.giftedlabs.echoinhealthbackend.dto.vault.FolderResponse;
import com.giftedlabs.echoinhealthbackend.entity.Folder;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.exception.ResourceNotFoundException;
import com.giftedlabs.echoinhealthbackend.repository.FolderRepository;
import com.giftedlabs.echoinhealthbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepository folderRepository;
    private final UserRepository userRepository;

    @Transactional
    public FolderResponse createFolder(CreateFolderRequest request, String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Folder parentFolder = null;
        if (request.getParentFolderId() != null) {
            parentFolder = folderRepository.findByIdAndUserId(request.getParentFolderId(), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Parent folder not found"));
        }

        if (folderRepository.existsByUserIdAndNameAndParentFolderId(
                userId, request.getName(), request.getParentFolderId())) {
            throw new IllegalArgumentException("Folder with this name already exists in this location");
        }

        Folder folder = Folder.builder()
                .user(user)
                .name(request.getName())
                .description(request.getDescription())
                .parentFolder(parentFolder)
                .build();

        Folder savedFolder = folderRepository.save(folder);
        return mapToResponse(savedFolder);
    }

    @Transactional(readOnly = true)
    public List<FolderResponse> getRootFolders(String userId) {
        return folderRepository.findByUserIdAndParentFolderIsNull(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FolderResponse> getSubFolders(String folderId, String userId) {
        return folderRepository.findByUserIdAndParentFolderId(userId, folderId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private FolderResponse mapToResponse(Folder folder) {
        return FolderResponse.builder()
                .id(folder.getId())
                .userId(folder.getUser().getId())
                .name(folder.getName())
                .description(folder.getDescription())
                .parentFolderId(folder.getParentFolder() != null ? folder.getParentFolder().getId() : null)
                .parentFolderName(folder.getParentFolder() != null ? folder.getParentFolder().getName() : null)
                .reportCount(0) // TODO: Implement count query
                .createdAt(folder.getCreatedAt())
                .build();
    }
}
