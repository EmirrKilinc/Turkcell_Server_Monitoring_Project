package com.monitoring.poc.servers;

import com.monitoring.poc.entity.Server;
import com.monitoring.poc.entity.User;
import com.monitoring.poc.enums.Role;
import com.monitoring.poc.enums.ServerStatus;
import com.monitoring.poc.enums.UserStatus;
import com.monitoring.poc.exception.ApiException;
import com.monitoring.poc.repository.ServerRepository;
import com.monitoring.poc.repository.UserRepository;
import com.monitoring.poc.security.CryptoUtil;
import com.monitoring.poc.servers.dto.ProvisionServerRequest;
import com.monitoring.poc.servers.dto.ServerResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServerServiceTest {

    private ServerRepository serverRepository;
    private UserRepository userRepository;
    private SshProvisioningService sshProvisioningService;
    private ServerService service;

    @BeforeEach
    void setUp() {
        serverRepository = mock(ServerRepository.class);
        userRepository = mock(UserRepository.class);
        sshProvisioningService = mock(SshProvisioningService.class);
        CryptoUtil cryptoUtil = new CryptoUtil("test-master-key-for-unit-tests-only");
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        service = new ServerService(serverRepository, userRepository, sshProvisioningService, cryptoUtil, passwordEncoder);

        User admin = new User("admin", "admin@x.com", "hash", Role.ADMIN, UserStatus.APPROVED);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        when(sshProvisioningService.provision(anyString(), anyString(), any(char[].class)))
                .thenReturn(new ProvisioningResult("web-01", "brand-new-secret"));
    }

    private ProvisionServerRequest request(String ip) {
        ProvisionServerRequest req = new ProvisionServerRequest();
        req.setIp(ip);
        req.setSshUsername("root");
        req.setSshPassword("pw".toCharArray());
        return req;
    }

    @Test
    void createsANewRowWhenHostnameHasNeverBeenSeen() {
        when(serverRepository.findByHostname("web-01")).thenReturn(Optional.empty());

        ServerResponse response = service.provision(request("10.0.0.5"), "admin");

        assertThat(response.getHostname()).isEqualTo("web-01");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");

        ArgumentCaptor<Server> captor = ArgumentCaptor.forClass(Server.class);
        verify(serverRepository).save(captor.capture());
        assertThat(captor.getValue().getHostname()).isEqualTo("web-01");
        assertThat(captor.getValue().getStatus()).isEqualTo(ServerStatus.ACTIVE);
    }

    @Test
    void rejectsAsConflictWhenAnActiveRowAlreadyOwnsTheHostname() {
        Server existingActive = new Server("web-01", "10.0.0.9", "root", "old-encrypted-secret",
                ServerStatus.ACTIVE, null);
        when(serverRepository.findByHostname("web-01")).thenReturn(Optional.of(existingActive));

        assertThatThrownBy(() -> service.provision(request("10.0.0.5"), "admin"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("web-01");

        verify(serverRepository, never()).save(any());
    }

    /**
     * Regression test: remove() is a soft delete (status -> REVOKED, row
     * kept). SSH provisioning above already deployed a brand new secret to
     * the target unconditionally, so re-adding a previously-removed host
     * must reuse/reactivate that same row with the fresh secret instead of
     * bouncing off it as a duplicate - otherwise the new secret is silently
     * discarded while the live agent on the target starts signing with it,
     * permanently desyncing the target from what the DB (and HmacAuthFilter)
     * expects.
     */
    @Test
    void reactivatesAPreviouslyRevokedRowInsteadOfRejectingItAsADuplicate() {
        Server existingRevoked = new Server("web-01", "10.0.0.9", "root", "old-encrypted-secret",
                ServerStatus.REVOKED, null);
        existingRevoked.setId(42L);
        when(serverRepository.findByHostname("web-01")).thenReturn(Optional.of(existingRevoked));

        ServerResponse response = service.provision(request("10.0.0.5"), "admin");

        assertThat(response.getId()).isEqualTo(42L);
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getIpAddress()).isEqualTo("10.0.0.5");

        ArgumentCaptor<Server> captor = ArgumentCaptor.forClass(Server.class);
        verify(serverRepository).save(captor.capture());
        Server saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(42L);
        assertThat(saved.getStatus()).isEqualTo(ServerStatus.ACTIVE);
        assertThat(saved.getIpAddress()).isEqualTo("10.0.0.5");
        assertThat(saved.getSecretKeyEncrypted()).isNotEqualTo("old-encrypted-secret");
    }
}
