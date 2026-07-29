package com.giftedlabs.echoinhealthbackend.security;

import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.exception.ResourceNotFoundException;
import com.giftedlabs.echoinhealthbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    public AuthenticatedUser requirePrincipal(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser;
        }

        throw new ResourceNotFoundException("Authenticated principal not found");
    }

    public User requireUser(Authentication authentication) {
        AuthenticatedUser principal = requirePrincipal(authentication);
        if (principal.getOrganizationId() == null) {
            return userRepository.findById(principal.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        }

        return userRepository.findByIdAndOrganizationId(principal.getUserId(), principal.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
