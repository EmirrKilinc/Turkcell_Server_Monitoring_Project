package com.monitoring.poc.repository;

import com.monitoring.poc.entity.MetricGroupItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MetricGroupItemRepository extends JpaRepository<MetricGroupItem, Long> {

    List<MetricGroupItem> findByGroup_Id(Long groupId);

    boolean existsByGroup_IdAndMetricDefinition_Id(Long groupId, Long metricDefinitionId);

    boolean existsByMetricDefinition_Id(Long metricDefinitionId);

    /**
     * Join-fetches group + definition so building an agent sync response
     * (one row per configured metric on the server) never N+1s.
     */
    @Query("SELECT i FROM MetricGroupItem i JOIN FETCH i.group g JOIN FETCH i.metricDefinition d " +
            "WHERE g.server.id = :serverId")
    List<MetricGroupItem> findAllForServer(@Param("serverId") Long serverId);
}
