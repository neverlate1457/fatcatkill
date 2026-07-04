package com.fatcatkill.repository;

import com.fatcatkill.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
    Optional<User> findByIdAndSessionToken(Long id, String sessionToken);
}
