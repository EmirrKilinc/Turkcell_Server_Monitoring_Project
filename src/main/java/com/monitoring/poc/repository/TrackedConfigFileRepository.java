package com.monitoring.poc.repository;

import com.monitoring.poc.entity.TrackedConfigFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrackedConfigFileRepository extends JpaRepository<TrackedConfigFile, Long> {

    List<TrackedConfigFile> findByServer_Id(Long serverId);

    boolean existsByServer_IdAndFilePath(Long serverId, String filePath);
}
