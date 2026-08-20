package com.monitoring.poc.servers;

import com.monitoring.poc.entity.ServerProvisioningScript;
import com.monitoring.poc.exception.ApiException;
import com.monitoring.poc.repository.ServerProvisioningScriptRepository;
import com.monitoring.poc.servers.dto.ProvisioningScriptRequest;
import com.monitoring.poc.servers.dto.ProvisioningScriptResponse;
import com.monitoring.poc.servers.dto.ProvisioningScriptUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServerProvisioningScriptServiceTest {

    private ServerProvisioningScriptRepository repository;
    private ServerProvisioningScriptService service;

    @BeforeEach
    void setUp() {
        repository = mock(ServerProvisioningScriptRepository.class);
        service = new ServerProvisioningScriptService(repository);
        when(repository.save(any(ServerProvisioningScript.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ServerProvisioningScript scriptWith(Long id, int order) {
        ServerProvisioningScript script = new ServerProvisioningScript("echo " + id, "desc-" + id, order);
        script.setId(id);
        return script;
    }

    @Test
    void createAppendsAtMaxOrderPlusOne() {
        when(repository.findAllByOrderByExecutionOrderAscIdAsc())
                .thenReturn(List.of(scriptWith(1L, 1), scriptWith(2L, 5)));

        ProvisioningScriptRequest request = new ProvisioningScriptRequest();
        request.setCommandLine("uptime");
        request.setDescription("Uptime kontrolu");

        ProvisioningScriptResponse response = service.create(request);

        assertThat(response.getExecutionOrder()).isEqualTo(6);
        assertThat(response.getCommandLine()).isEqualTo("uptime");
    }

    @Test
    void createOnAnEmptyTableStartsAtOne() {
        when(repository.findAllByOrderByExecutionOrderAscIdAsc()).thenReturn(List.of());

        ProvisioningScriptRequest request = new ProvisioningScriptRequest();
        request.setCommandLine("uptime");
        request.setDescription("Uptime kontrolu");

        ProvisioningScriptResponse response = service.create(request);

        assertThat(response.getExecutionOrder()).isEqualTo(1);
    }

    @Test
    void moveUpSwapsExecutionOrderWithThePreviousRow() {
        ServerProvisioningScript first = scriptWith(1L, 1);
        ServerProvisioningScript second = scriptWith(2L, 2);
        when(repository.findAllByOrderByExecutionOrderAscIdAsc()).thenReturn(List.of(first, second));

        service.moveUp(2L);

        assertThat(second.getExecutionOrder()).isEqualTo(1);
        assertThat(first.getExecutionOrder()).isEqualTo(2);
    }

    @Test
    void moveDownSwapsExecutionOrderWithTheNextRow() {
        ServerProvisioningScript first = scriptWith(1L, 1);
        ServerProvisioningScript second = scriptWith(2L, 2);
        when(repository.findAllByOrderByExecutionOrderAscIdAsc()).thenReturn(List.of(first, second));

        service.moveDown(1L);

        assertThat(first.getExecutionOrder()).isEqualTo(2);
        assertThat(second.getExecutionOrder()).isEqualTo(1);
    }

    @Test
    void movingTheTopRowUpIsConflict() {
        ServerProvisioningScript first = scriptWith(1L, 1);
        ServerProvisioningScript second = scriptWith(2L, 2);
        when(repository.findAllByOrderByExecutionOrderAscIdAsc()).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.moveUp(1L)).isInstanceOf(ApiException.class);
    }

    @Test
    void movingTheBottomRowDownIsConflict() {
        ServerProvisioningScript first = scriptWith(1L, 1);
        ServerProvisioningScript second = scriptWith(2L, 2);
        when(repository.findAllByOrderByExecutionOrderAscIdAsc()).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.moveDown(2L)).isInstanceOf(ApiException.class);
    }

    @Test
    void deleteRemovesTheRow() {
        ServerProvisioningScript script = scriptWith(1L, 1);
        when(repository.findById(1L)).thenReturn(Optional.of(script));

        service.delete(1L);

        org.mockito.Mockito.verify(repository).delete(script);
    }

    @Test
    void updateTogglesEnabledWithoutTouchingOtherFields() {
        ServerProvisioningScript script = scriptWith(1L, 1);
        when(repository.findById(1L)).thenReturn(Optional.of(script));

        ProvisioningScriptUpdateRequest request = new ProvisioningScriptUpdateRequest();
        request.setIsEnabled(false);

        ProvisioningScriptResponse response = service.update(1L, request);

        assertThat(response.getIsEnabled()).isFalse();
        assertThat(response.getCommandLine()).isEqualTo(script.getCommandLine());
    }
}
