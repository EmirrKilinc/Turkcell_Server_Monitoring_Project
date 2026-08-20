package com.monitoring.poc.repository;

import com.monitoring.poc.entity.User;
import com.monitoring.poc.enums.Role;
import com.monitoring.poc.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<User> findByStatus(UserStatus status);

    List<User> findByRole(Role role);

    long countByRole(Role role);
}
