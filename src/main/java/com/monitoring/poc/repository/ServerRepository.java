package com.monitoring.poc.repository;

import com.monitoring.poc.entity.Server;
import com.monitoring.poc.enums.ServerStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServerRepository extends JpaRepository<Server, Long> {

    Optional<Server> findByHostname(String hostname);

    List<Server> findByStatus(ServerStatus status);
}
