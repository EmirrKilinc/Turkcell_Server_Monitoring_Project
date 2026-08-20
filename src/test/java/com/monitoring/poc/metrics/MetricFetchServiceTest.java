package com.monitoring.poc.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.poc.entity.MetricDefinition;
import com.monitoring.poc.entity.MetricFetchRequest;
import com.monitoring.poc.entity.MetricGroup;
import com.monitoring.poc.entity.MetricGroupItem;
import com.monitoring.poc.entity.Server;
import com.monitoring.poc.enums.ExecutionMode;
import com.monitoring.poc.enums.FetchRequestStatus;
import com.monitoring.poc.enums.MetricValueType;
import com.monitoring.poc.enums.ServerStatus;
import com.monitoring.poc.exception.ApiException;
import com.monitoring.poc.metrics.dto.FetchNowResponse;
import com.monitoring.poc.repository.MetricFetchRequestRepository;
import com.monitoring.poc.repository.MetricGroupItemRepository;
import com.monitoring.poc.repository.MetricGroupRepository;
import com.monitoring.poc.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetricFetchServiceTest {

    private MetricFetchRequestRepository metricFetchRequestRepository;
    private MetricGroupRepository metricGroupRepository;
    private MetricGroupItemRepository metricGroupItemRepository;
    private UserRepository userRepository;
    private MetricFetchService service;

    private Server server;
    private MetricGroup group;
    private MetricGroupItem item;

    @BeforeEach
    void setUp() {
        metricFetchRequestRepository = mock(MetricFetchRequestRepository.class);
        metricGroupRepository = mock(MetricGroupRepository.class);
        metricGroupItemRepository = mock(MetricGroupItemRepository.class);
        userRepository = mock(UserRepository.class);

        // Fast poll settings so the timeout-path test doesn't slow the suite down.
        service = new MetricFetchService(metricFetchRequestRepository, metricGroupRepository,
                metricGroupItemRepository, userRepository, new ObjectMapper(),
                5L, 4, 2L);

        server = new Server("web-01", "10.0.0.5", "monitoring_user", "enc", ServerStatus.ACTIVE, null);
        server.setId(1L);
        group = new MetricGroup(server, "Database Metrics", ExecutionMode.PERIODIC, 15);
        group.setId(10L);
        MetricDefinition definition = new MetricDefinition("App Health", "app_health", "APPLICATION",
                "curl -sf http://localhost:8080/actuator/health", 3, MetricValueType.RAW, null, null);
        definition.setId(20L);
        item = new MetricGroupItem(group, definition, null);
        item.setId(30L);
    }

    @Test
    void fetchNowRejectsMissingGroup() {
        when(metricGroupRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.fetchNow(10L, "admin")).isInstanceOf(ApiException.class);
    }

    @Test
    void fetchNowRejectsEmptyGroup() {
        when(metricGroupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(metricGroupItemRepository.findByGroup_Id(10L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.fetchNow(10L, "admin")).isInstanceOf(ApiException.class);
    }

    @Test
    void fetchNowAllowsViewerOnAnyApprovedGroup() {
        when(metricGroupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(metricGroupItemRepository.findByGroup_Id(10L)).thenReturn(List.of(item));
        when(metricFetchRequestRepository.save(any())).thenAnswer(inv -> {
            MetricFetchRequest saved = inv.getArgument(0);
            saved.setId(99L);
            return saved;
        });
        // Never completes within the poll window -> falls through to PENDING/202.
        when(metricFetchRequestRepository.findById(99L)).thenReturn(Optional.of(pendingRequest(99L)));

        FetchNowResponse response = service.fetchNow(10L, "viewer");

        assertThat(response.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void pollLoopReturnsAsSoonAsAgentCompletesMidWait() {
        when(metricGroupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(metricGroupItemRepository.findByGroup_Id(10L)).thenReturn(List.of(item));
        when(metricFetchRequestRepository.save(any())).thenAnswer(inv -> {
            MetricFetchRequest saved = inv.getArgument(0);
            saved.setId(99L);
            return saved;
        });

        MetricFetchRequest completed = pendingRequest(99L);
        completed.setStatus(FetchRequestStatus.COMPLETED);
        completed.setResultPayload("{\"cpu\":1}");

        // Two PENDING reads (agent hasn't reported yet), then COMPLETED.
        when(metricFetchRequestRepository.findById(99L)).thenReturn(
                Optional.of(pendingRequest(99L)),
                Optional.of(pendingRequest(99L)),
                Optional.of(completed));

        FetchNowResponse response = service.fetchNow(10L, "admin");

        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getFetchRequestId()).isEqualTo(99L);
    }

    @Test
    void pollLoopFallsBackToPendingOnTimeout() {
        when(metricGroupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(metricGroupItemRepository.findByGroup_Id(10L)).thenReturn(List.of(item));
        when(metricFetchRequestRepository.save(any())).thenAnswer(inv -> {
            MetricFetchRequest saved = inv.getArgument(0);
            saved.setId(99L);
            return saved;
        });
        when(metricFetchRequestRepository.findById(99L)).thenReturn(Optional.of(pendingRequest(99L)));

        FetchNowResponse response = service.fetchNow(10L, "admin");

        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getPollUrl()).contains("99");
    }

    @Test
    void completeFetchRequestIsIdempotent() {
        MetricFetchRequest pending = pendingRequest(99L);
        when(metricFetchRequestRepository.findById(99L)).thenReturn(Optional.of(pending));

        service.completeFetchRequest(99L, "{\"a\":1}");
        assertThat(pending.getStatus()).isEqualTo(FetchRequestStatus.COMPLETED);

        // Simulate a second, late report arriving after the row already settled.
        pending.setResultPayload("{\"a\":1}");
        service.completeFetchRequest(99L, "{\"a\":2}");

        assertThat(pending.getResultPayload()).isEqualTo("{\"a\":1}");

        ArgumentCaptor<MetricFetchRequest> captor = ArgumentCaptor.forClass(MetricFetchRequest.class);
        verify(metricFetchRequestRepository, times(1)).save(captor.capture());
    }

    private MetricFetchRequest pendingRequest(Long id) {
        MetricFetchRequest request = new MetricFetchRequest(10L, 1L, null, FetchRequestStatus.PENDING);
        request.setId(id);
        return request;
    }
}
