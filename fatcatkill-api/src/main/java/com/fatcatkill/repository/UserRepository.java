package com.fatcatkill.repository;

import com.fatcatkill.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsernameIgnoreCase(String username);
    Optional<User> findByUsernameKey(String usernameKey);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByUsernameKey(String usernameKey);
    Optional<User> findByIdAndSessionToken(Long id, String sessionToken);
}
