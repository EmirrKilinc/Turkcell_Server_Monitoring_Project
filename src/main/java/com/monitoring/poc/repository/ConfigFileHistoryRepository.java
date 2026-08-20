package com.monitoring.poc.repository;

import com.monitoring.poc.entity.ConfigFileHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConfigFileHistoryRepository extends JpaRepository<ConfigFileHistory, Long> {

    List<ConfigFileHistory> findByTrackedFileIdOrderByVersionNumberDesc(Long trackedFileId);

    Optional<ConfigFileHistory> findByTrackedFileIdAndVersionNumber(Long trackedFileId, Integer versionNumber);

    long countByTrackedFileId(Long trackedFileId);
}
