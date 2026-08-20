package com.monitoring.poc.servers;

import com.monitoring.poc.entity.ServerProvisioningScript;
import com.monitoring.poc.exception.ApiException;
import com.monitoring.poc.repository.ServerProvisioningScriptRepository;
import com.monitoring.poc.servers.dto.ProvisioningScriptRequest;
import com.monitoring.poc.servers.dto.ProvisioningScriptResponse;
import com.monitoring.poc.servers.dto.ProvisioningScriptUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ServerProvisioningScriptService {

    private final ServerProvisioningScriptRepository repository;

    public ServerProvisioningScriptService(ServerProvisioningScriptRepository repository) {
        this.repository = repository;
    }

    public List<ProvisioningScriptResponse> listAll() {
        return repository.findAllByOrderByExecutionOrderAscIdAsc().stream()
                .map(ProvisioningScriptResponse::new)
                .toList();
    }

    @Transactional
    public ProvisioningScriptResponse create(ProvisioningScriptRequest request) {
        int nextOrder = repository.findAllByOrderByExecutionOrderAscIdAsc().stream()
                .mapToInt(ServerProvisioningScript::getExecutionOrder)
                .max()
                .orElse(0) + 1;

        ServerProvisioningScript script = new ServerProvisioningScript(
                request.getCommandLine(), request.getDescription(), nextOrder);
        repository.save(script);
        return new ProvisioningScriptResponse(script);
    }

    @Transactional
    public ProvisioningScriptResponse update(Long id, ProvisioningScriptUpdateRequest request) {
        ServerProvisioningScript script = findOrThrow(id);
        if (request.getCommandLine() != null) {
            script.setCommandLine(request.getCommandLine());
        }
        if (request.getDescription() != null) {
            script.setDescription(request.getDescription());
        }
        if (request.getIsEnabled() != null) {
            script.setIsEnabled(request.getIsEnabled());
        }
        repository.save(script);
        return new ProvisioningScriptResponse(script);
    }

    @Transactional
    public void delete(Long id) {
        ServerProvisioningScript script = findOrThrow(id);
        repository.delete(script);
    }

    @Transactional
    public ProvisioningScriptResponse moveUp(Long id) {
        return swapWithNeighbor(id, -1, "Bu komut zaten en ustte");
    }

    @Transactional
    public ProvisioningScriptResponse moveDown(Long id) {
        return swapWithNeighbor(id, 1, "Bu komut zaten en altta");
    }

    private ProvisioningScriptResponse swapWithNeighbor(Long id, int direction, String boundaryMessage) {
        List<ServerProvisioningScript> ordered = repository.findAllByOrderByExecutionOrderAscIdAsc();
        int index = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).getId().equals(id)) {
                index = i;
                break;
            }
        }
        if (index == -1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Provisioning script bulunamadi");
        }
        int neighborIndex = index + direction;
        if (neighborIndex < 0 || neighborIndex >= ordered.size()) {
            throw new ApiException(HttpStatus.CONFLICT, boundaryMessage);
        }

        ServerProvisioningScript current = ordered.get(index);
        ServerProvisioningScript neighbor = ordered.get(neighborIndex);
        int currentOrder = current.getExecutionOrder();
        current.setExecutionOrder(neighbor.getExecutionOrder());
        neighbor.setExecutionOrder(currentOrder);
        repository.save(current);
        repository.save(neighbor);

        return new ProvisioningScriptResponse(current);
    }

    private ServerProvisioningScript findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Provisioning script bulunamadi"));
    }
}
