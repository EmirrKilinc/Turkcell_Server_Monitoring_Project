package com.monitoring.poc.entity;

import com.monitoring.poc.enums.ExecutionMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "metric_group_items")
public class MetricGroupItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "group_id", nullable = false)
    private MetricGroup group;

    @ManyToOne
    @JoinColumn(name = "metric_definition_id", nullable = false)
    private MetricDefinition metricDefinition;

    @Enumerated(EnumType.STRING)
    @Column(name = "override_execution_mode", length = 20)
    private ExecutionMode overrideExecutionMode;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public MetricGroupItem() {
    }

    public MetricGroupItem(MetricGroup group, MetricDefinition metricDefinition, ExecutionMode overrideExecutionMode) {
        this.group = group;
        this.metricDefinition = metricDefinition;
        this.overrideExecutionMode = overrideExecutionMode;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public MetricGroup getGroup() {
        return group;
    }

    public void setGroup(MetricGroup group) {
        this.group = group;
    }

    public MetricDefinition getMetricDefinition() {
        return metricDefinition;
    }

    public void setMetricDefinition(MetricDefinition metricDefinition) {
        this.metricDefinition = metricDefinition;
    }

    public ExecutionMode getOverrideExecutionMode() {
        return overrideExecutionMode;
    }

    public void setOverrideExecutionMode(ExecutionMode overrideExecutionMode) {
        this.overrideExecutionMode = overrideExecutionMode;
    }

    public LocalDateTime getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(LocalDateTime lastRunAt) {
        this.lastRunAt = lastRunAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
