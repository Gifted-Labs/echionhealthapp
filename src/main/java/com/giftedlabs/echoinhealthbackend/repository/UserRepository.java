package com.giftedlabs.echoinhealthbackend.repository;

import com.giftedlabs.echoinhealthbackend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for User entity operations
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {

    /**
     * Find a user by email address
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if a user exists with the given email
     */
    boolean existsByEmail(String email);

    /**
     * Find a user by email and check if email is verified
     */
    Optional<User> findByEmailAndEmailVerified(String email, Boolean emailVerified);
}
