package com.monitoring.poc.repository;

import com.monitoring.poc.entity.ServerProvisioningScript;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServerProvisioningScriptRepository extends JpaRepository<ServerProvisioningScript, Long> {

    List<ServerProvisioningScript> findAllByOrderByExecutionOrderAscIdAsc();

    List<ServerProvisioningScript> findByIsEnabledTrueOrderByExecutionOrderAscIdAsc();
}
