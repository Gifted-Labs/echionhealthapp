package com.giftedlabs.echoinhealthbackend.security;

import com.giftedlabs.echoinhealthbackend.entity.Role;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

@Getter
public class AuthenticatedUser extends User {

    private final String userId;
    private final String organizationId;
    private final Role role;

    public AuthenticatedUser(
            String userId,
            String organizationId,
            String username,
            String password,
            Role role,
            boolean active,
            boolean accountLocked,
            Collection<? extends GrantedAuthority> authorities) {
        super(username, password, active, true, true, !accountLocked, authorities);
        this.userId = userId;
        this.organizationId = organizationId;
        this.role = role;
    }
}
