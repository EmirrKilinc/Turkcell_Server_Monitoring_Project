package com.monitoring.poc.servers;

import com.monitoring.poc.entity.Server;
import com.monitoring.poc.entity.User;
import com.monitoring.poc.enums.ServerStatus;
import com.monitoring.poc.exception.ApiException;
import com.monitoring.poc.repository.ServerRepository;
import com.monitoring.poc.repository.UserRepository;
import com.monitoring.poc.security.CryptoUtil;
import com.monitoring.poc.servers.dto.ProvisionServerRequest;
import com.monitoring.poc.servers.dto.ServerResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class ServerService {

    // Root is used once, in-memory only, to bootstrap the restricted
    // monitoring_user account and drop the agent files into place; it is
    // never persisted and never used again after provision() returns.
    private static final String DEFAULT_SSH_USERNAME = "root";

    private final ServerRepository serverRepository;
    private final UserRepository userRepository;
    private final SshProvisioningService sshProvisioningService;
    private final CryptoUtil cryptoUtil;
    private final PasswordEncoder passwordEncoder;

    public ServerService(ServerRepository serverRepository,
                          UserRepository userRepository,
                          SshProvisioningService sshProvisioningService,
                          CryptoUtil cryptoUtil,
                          PasswordEncoder passwordEncoder) {
        this.serverRepository = serverRepository;
        this.userRepository = userRepository;
        this.sshProvisioningService = sshProvisioningService;
        this.cryptoUtil = cryptoUtil;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public ServerResponse provision(ProvisionServerRequest request, String requestingUsername) {
        String sshUsername = request.getSshUsername() == null || request.getSshUsername().isBlank()
                ? DEFAULT_SSH_USERNAME
                : request.getSshUsername();

        // Password lives only in this call frame; never persisted or logged.
        // Wiped here too (belt-and-suspenders) in case provision() throws
        // before it gets a chance to clear its own copy of the same array.
        char[] sshPassword = request.getSshPassword();
        ProvisioningResult result;
        try {
            result = sshProvisioningService.provision(request.getIp(), sshUsername, sshPassword);
        } finally {
            if (sshPassword != null) {
                Arrays.fill(sshPassword, '\0');
            }
        }

        User addedBy = userRepository.findByUsername(requestingUsername).orElse(null);

        // A hostname row can already exist in REVOKED state from a previous
        // "Sunucu Kaldir" (remove() is a soft delete - it never deletes the
        // row, only flips status). SSH provisioning above already generated
        // a brand new secret and deployed it to the target unconditionally,
        // so re-adding a previously-removed host must reactivate that same
        // row with the fresh secret, not bounce off it as a duplicate - a
        // hard conflict only makes sense against a still-ACTIVE row, since
        // that means some other, currently-working registration would be
        // silently overwritten.
        Server server = serverRepository.findByHostname(result.hostname()).orElse(null);
        if (server != null && server.getStatus() == ServerStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Bu sunucu (" + result.hostname() + ") zaten izlemede kayitli");
        }

        if (server == null) {
            server = new Server(
                    result.hostname(),
                    request.getIp(),
                    sshUsername,
                    cryptoUtil.encrypt(result.rawSecretKey()),
                    ServerStatus.ACTIVE,
                    addedBy
            );
        } else {
            server.setIpAddress(request.getIp());
            server.setSshUsername(sshUsername);
            server.setSecretKeyEncrypted(cryptoUtil.encrypt(result.rawSecretKey()));
            server.setStatus(ServerStatus.ACTIVE);
            server.setAddedBy(addedBy);
            server.setUpdatedAt(LocalDateTime.now());
        }
        serverRepository.save(server);

        return new ServerResponse(server);
    }

    public List<ServerResponse> listActive() {
        return serverRepository.findByStatus(ServerStatus.ACTIVE).stream()
                .map(ServerResponse::new)
                .toList();
    }

    @Transactional
    public void remove(Long serverId, String submittedPassword, String requestingUsername) {
        User user = userRepository.findByUsername(requestingUsername)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Kullanici bulunamadi"));

        if (!passwordEncoder.matches(submittedPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sifre dogrulanamadi");
        }

        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Sunucu bulunamadi"));

        server.setStatus(ServerStatus.REVOKED);
        server.setUpdatedAt(LocalDateTime.now());
        serverRepository.save(server);
    }
}
