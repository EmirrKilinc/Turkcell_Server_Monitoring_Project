package com.monitoring.poc.metrics;

import com.monitoring.poc.entity.MetricDefinition;
import com.monitoring.poc.entity.MetricGroup;
import com.monitoring.poc.entity.MetricGroupItem;
import com.monitoring.poc.entity.Server;
import com.monitoring.poc.enums.ExecutionMode;
import com.monitoring.poc.enums.MetricValueType;
import com.monitoring.poc.enums.ServerStatus;
import com.monitoring.poc.exception.ApiException;
import com.monitoring.poc.metrics.dto.MetricGroupItemRequest;
import com.monitoring.poc.metrics.dto.MetricGroupItemResponse;
import com.monitoring.poc.metrics.dto.MetricGroupRequest;
import com.monitoring.poc.metrics.dto.MetricGroupResponse;
import com.monitoring.poc.repository.CustomMetricLogRepository;
import com.monitoring.poc.repository.MetricDefinitionRepository;
import com.monitoring.poc.repository.MetricGroupItemRepository;
import com.monitoring.poc.repository.MetricGroupRepository;
import com.monitoring.poc.repository.ServerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetricGroupServiceTest {

    private MetricGroupRepository metricGroupRepository;
    private MetricGroupItemRepository metricGroupItemRepository;
    private MetricDefinitionRepository metricDefinitionRepository;
    private ServerRepository serverRepository;
    private MetricGroupService service;

    private Server server;

    @BeforeEach
    void setUp() {
        metricGroupRepository = mock(MetricGroupRepository.class);
        metricGroupItemRepository = mock(MetricGroupItemRepository.class);
        metricDefinitionRepository = mock(MetricDefinitionRepository.class);
        CustomMetricLogRepository customMetricLogRepository = mock(CustomMetricLogRepository.class);
        serverRepository = mock(ServerRepository.class);
        service = new MetricGroupService(metricGroupRepository, metricGroupItemRepository,
                metricDefinitionRepository, customMetricLogRepository, serverRepository);

        server = new Server("web-01", "10.0.0.5", "monitoring_user", "enc", ServerStatus.ACTIVE, null);
        server.setId(1L);
    }

    private MetricGroupRequest groupRequest() {
        MetricGroupRequest req = new MetricGroupRequest();
        req.setServerId(1L);
        req.setName("Database Metrics");
        req.setDefaultExecutionMode(ExecutionMode.PERIODIC);
        req.setIntervalSeconds(15);
        return req;
    }

    @Test
    void createsGroupWhenServerExistsAndNameIsUnique() {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(server));
        when(metricGroupRepository.existsByServer_IdAndName(1L, "Database Metrics")).thenReturn(false);

        MetricGroupResponse response = service.create(groupRequest());

        assertThat(response.getName()).isEqualTo("Database Metrics");
        assertThat(response.getServerHostname()).isEqualTo("web-01");
    }

    @Test
    void rejectsCreateWhenServerIsMissing() {
        when(serverRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(groupRequest())).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsCreateAsConflictWhenGroupNameAlreadyExistsOnServer() {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(server));
        when(metricGroupRepository.existsByServer_IdAndName(1L, "Database Metrics")).thenReturn(true);

        assertThatThrownBy(() -> service.create(groupRequest())).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsCreateWhenIntervalIsNotPositive() {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(server));
        MetricGroupRequest req = groupRequest();
        req.setIntervalSeconds(0);

        assertThatThrownBy(() -> service.create(req)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsAddingTheSameDefinitionToAGroupTwice() {
        MetricGroup group = new MetricGroup(server, "Database Metrics", ExecutionMode.PERIODIC, 15);
        group.setId(10L);
        MetricDefinition definition = new MetricDefinition("PG Conns", "pg_conns", "DATABASE",
                "echo test", 3, MetricValueType.RAW, null, null);
        definition.setId(20L);

        when(metricGroupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(metricDefinitionRepository.findById(20L)).thenReturn(Optional.of(definition));
        when(metricGroupItemRepository.existsByGroup_IdAndMetricDefinition_Id(10L, 20L)).thenReturn(true);

        MetricGroupItemRequest itemRequest = new MetricGroupItemRequest();
        itemRequest.setMetricDefinitionId(20L);

        assertThatThrownBy(() -> service.addItem(10L, itemRequest)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsAddingANonApprovedDefinitionToAGroup() {
        MetricGroup group = new MetricGroup(server, "Database Metrics", ExecutionMode.PERIODIC, 15);
        group.setId(10L);
        MetricDefinition definition = new MetricDefinition("PG Conns", "pg_conns", "DATABASE",
                "echo test", 3, MetricValueType.RAW, null, null);
        definition.setId(20L);
        definition.setApprovalStatus(com.monitoring.poc.enums.ApprovalStatus.PENDING_APPROVAL);

        when(metricGroupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(metricDefinitionRepository.findById(20L)).thenReturn(Optional.of(definition));

        MetricGroupItemRequest itemRequest = new MetricGroupItemRequest();
        itemRequest.setMetricDefinitionId(20L);

        assertThatThrownBy(() -> service.addItem(10L, itemRequest)).isInstanceOf(ApiException.class);
    }

    @Test
    void addsItemWithNoOverrideAndInheritsGroupMode() {
        MetricGroup group = new MetricGroup(server, "Database Metrics", ExecutionMode.PERIODIC, 15);
        group.setId(10L);
        MetricDefinition definition = new MetricDefinition("PG Conns", "pg_conns", "DATABASE",
                "echo test", 3, MetricValueType.RAW, null, null);
        definition.setId(20L);

        when(metricGroupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(metricDefinitionRepository.findById(20L)).thenReturn(Optional.of(definition));
        when(metricGroupItemRepository.existsByGroup_IdAndMetricDefinition_Id(10L, 20L)).thenReturn(false);

        MetricGroupItemRequest itemRequest = new MetricGroupItemRequest();
        itemRequest.setMetricDefinitionId(20L);

        MetricGroupItemResponse response = service.addItem(10L, itemRequest);

        assertThat(response.getOverrideExecutionMode()).isNull();
        assertThat(response.getEffectiveExecutionMode()).isEqualTo("PERIODIC");
    }

    @Test
    void resolveEffectiveModeReturnsOverrideWhenPresent() {
        MetricGroup group = new MetricGroup(server, "Database Metrics", ExecutionMode.PERIODIC, 15);
        MetricDefinition definition = new MetricDefinition("PG Conns", "pg_conns", "DATABASE",
                "echo test", 3, MetricValueType.RAW, null, null);
        MetricGroupItem item = new MetricGroupItem(group, definition, ExecutionMode.ON_DEMAND);

        assertThat(MetricGroupService.resolveEffectiveMode(item)).isEqualTo(ExecutionMode.ON_DEMAND);
    }

    @Test
    void resolveEffectiveModeFallsBackToGroupDefaultWhenNoOverride() {
        MetricGroup group = new MetricGroup(server, "Database Metrics", ExecutionMode.PERIODIC, 15);
        MetricDefinition definition = new MetricDefinition("PG Conns", "pg_conns", "DATABASE",
                "echo test", 3, MetricValueType.RAW, null, null);
        MetricGroupItem item = new MetricGroupItem(group, definition, null);

        assertThat(MetricGroupService.resolveEffectiveMode(item)).isEqualTo(ExecutionMode.PERIODIC);
    }
}
