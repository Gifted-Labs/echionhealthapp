package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.security.AuthenticatedUser;
import com.giftedlabs.echoinhealthbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

import static com.giftedlabs.echoinhealthbackend.util.CacheNames.AUTH_USERS;

/**
 * Custom UserDetailsService for Spring Security authentication
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

        private final UserRepository userRepository;

        @Override
        @Cacheable(value = AUTH_USERS, key = "#email")
        public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new UsernameNotFoundException(
                                                "User not found with email: " + email));

                return new AuthenticatedUser(
                                user.getId(),
                                user.getOrganizationId(),
                                user.getEmail(),
                                user.getPasswordHash(),
                                user.getRole(),
                                Boolean.TRUE.equals(user.getActive()),
                                user.getAccountLocked(),
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        }
}
