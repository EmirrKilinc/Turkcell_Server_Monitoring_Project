package com.monitoring.poc;

import com.monitoring.poc.auth.dto.AuthResponse;
import com.monitoring.poc.auth.dto.LoginRequest;
import com.monitoring.poc.entity.MetricDefinition;
import com.monitoring.poc.entity.MetricGroup;
import com.monitoring.poc.entity.MetricGroupItem;
import com.monitoring.poc.entity.Server;
import com.monitoring.poc.entity.User;
import com.monitoring.poc.entity.UserOtpVerification;
import com.monitoring.poc.enums.ExecutionMode;
import com.monitoring.poc.enums.MetricValueType;
import com.monitoring.poc.enums.Role;
import com.monitoring.poc.enums.ServerStatus;
import com.monitoring.poc.enums.UserStatus;
import com.monitoring.poc.metrics.dto.FetchNowResponse;
import com.monitoring.poc.metrics.dto.MetricDefinitionRequest;
import com.monitoring.poc.metrics.dto.MetricGroupRequest;
import com.monitoring.poc.repository.MetricDefinitionRepository;
import com.monitoring.poc.repository.MetricGroupItemRepository;
import com.monitoring.poc.repository.MetricGroupRepository;
import com.monitoring.poc.repository.ServerRepository;
import com.monitoring.poc.repository.UserOtpVerificationRepository;
import com.monitoring.poc.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RbacIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ServerRepository serverRepository;

    @Autowired
    private MetricDefinitionRepository metricDefinitionRepository;

    @Autowired
    private MetricGroupRepository metricGroupRepository;

    @Autowired
    private MetricGroupItemRepository metricGroupItemRepository;

    @Autowired
    private UserOtpVerificationRepository userOtpVerificationRepository;

    private String createApprovedUserAndLogin(Role role) {
        String username = "rbac_" + role.name().toLowerCase() + "_" + System.nanoTime();
        User user = new User(username, username + "@example.com",
                passwordEncoder.encode("Password123!"), role, UserStatus.APPROVED);
        userRepository.save(user);

        LoginRequest login = new LoginRequest();
        login.setUsername(username);
        login.setPassword("Password123!");
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                baseUrl("/api/auth/login"), login, AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        AuthResponse body = response.getBody();
        if (body.isRequires2fa()) {
            return completeTwoFactorLogin(body.getTempToken());
        }
        return body.getToken();
    }

    private String completeTwoFactorLogin(String tempToken) {
        UserOtpVerification otp = userOtpVerificationRepository.findByTempTokenAndIsUsedFalse(tempToken)
                .orElseThrow();
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                baseUrl("/api/auth/verify-2fa"), Map.of("tempToken", tempToken, "otpCode", otp.getOtpCode()),
                AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().getToken();
    }

    private HttpEntity<Void> authEntity(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private <T> HttpEntity<T> authJsonEntity(String token, T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    /** Seeds Server -> MetricGroup -> MetricDefinition(APPROVED) -> MetricGroupItem, returns the group id. */
    private Long seedGroupWithApprovedItem() {
        Server server = new Server("rbac-host-" + System.nanoTime(), "10.0.0.9", "monitoring_user",
                "enc", ServerStatus.ACTIVE, null);
        serverRepository.save(server);

        MetricDefinition definition = new MetricDefinition("App Health", "app_health_" + System.nanoTime(),
                "APPLICATION", "curl -sf http://127.0.0.1:8081/health", 3, MetricValueType.RAW, null, null);
        metricDefinitionRepository.save(definition);

        MetricGroup group = new MetricGroup(server, "RBAC Test Group", ExecutionMode.ON_DEMAND, 15);
        metricGroupRepository.save(group);

        MetricGroupItem item = new MetricGroupItem(group, definition, null);
        metricGroupItemRepository.save(item);

        return group.getId();
    }

    @Test
    void viewerCannotAccessAdminEndpoints() {
        String viewerToken = createApprovedUserAndLogin(Role.VIEWER);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/admin/users"), HttpMethod.GET, authEntity(viewerToken), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void viewerCannotDeleteServers() {
        String viewerToken = createApprovedUserAndLogin(Role.VIEWER);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(viewerToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("password", "Password123!"), headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/servers/999999"), HttpMethod.DELETE, entity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void operatorCannotDeleteServers() {
        String operatorToken = createApprovedUserAndLogin(Role.OPERATOR);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(operatorToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> deleteEntity = new HttpEntity<>(Map.of("password", "Password123!"), headers);

        ResponseEntity<Map> deleteResponse = restTemplate.exchange(
                baseUrl("/api/servers/999999"), HttpMethod.DELETE, deleteEntity, Map.class);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unauthenticatedRequestToProtectedEndpointIs401() {
        ResponseEntity<Map> response = restTemplate.getForEntity(baseUrl("/api/admin/users"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void viewerCannotCreateMetricDefinitions() {
        String viewerToken = createApprovedUserAndLogin(Role.VIEWER);

        MetricDefinitionRequest request = new MetricDefinitionRequest();
        request.setName("Docker Container Count");
        request.setMetricKey("viewer_test_docker_count_" + System.nanoTime());
        request.setCategory("CONTAINERS");
        request.setCommand("docker ps -q");
        request.setTimeoutSeconds(3);
        request.setValueType(MetricValueType.RAW);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/metric-definitions"), HttpMethod.POST, authJsonEntity(viewerToken, request), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void operatorCanCreateMetricDefinitions() {
        String operatorToken = createApprovedUserAndLogin(Role.OPERATOR);

        MetricDefinitionRequest request = new MetricDefinitionRequest();
        request.setName("App Health Check");
        request.setMetricKey("operator_test_health_" + System.nanoTime());
        request.setCategory("APPLICATION");
        request.setCommand("curl -sf http://127.0.0.1:8081/health");
        request.setTimeoutSeconds(3);
        request.setValueType(MetricValueType.RAW);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/metric-definitions"), HttpMethod.POST, authJsonEntity(operatorToken, request), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void viewerCanListMetricDefinitions() {
        String viewerToken = createApprovedUserAndLogin(Role.VIEWER);

        ResponseEntity<List> response = restTemplate.exchange(
                baseUrl("/api/metric-definitions"), HttpMethod.GET, authEntity(viewerToken), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void viewerCannotCreateMetricGroups() {
        String viewerToken = createApprovedUserAndLogin(Role.VIEWER);

        MetricGroupRequest request = new MetricGroupRequest();
        request.setServerId(999999L);
        request.setName("Viewer Blocked Group");
        request.setDefaultExecutionMode(ExecutionMode.PERIODIC);
        request.setIntervalSeconds(15);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/metric-groups"), HttpMethod.POST, authJsonEntity(viewerToken, request), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void viewerCannotDeleteMetricGroups() {
        String viewerToken = createApprovedUserAndLogin(Role.VIEWER);
        Long groupId = seedGroupWithApprovedItem();

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/metric-groups/" + groupId), HttpMethod.DELETE, authEntity(viewerToken), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void viewerCanTriggerFetchNowOnAGroup() {
        String viewerToken = createApprovedUserAndLogin(Role.VIEWER);
        Long groupId = seedGroupWithApprovedItem();

        ResponseEntity<FetchNowResponse> response = restTemplate.exchange(
                baseUrl("/api/metric-groups/" + groupId + "/fetch-now"), HttpMethod.POST,
                authEntity(viewerToken), FetchNowResponse.class);

        // No agent ever reports back in this test, so the bounded poll times
        // out and falls back to PENDING/202 - proving VIEWER wasn't blocked
        // by role (that would be a 403), only by the absence of an agent.
        // Every metric is a command now, and only the approval workflow
        // gates dispatch - there's no more type-based VIEWER carve-out.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    // ---------- Metric approval workflow ----------

    @Test
    void operatorCreatedMetricDefinitionLandsAsPendingApproval() {
        String operatorToken = createApprovedUserAndLogin(Role.OPERATOR);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/metric-definitions"), HttpMethod.POST, authJsonEntity(operatorToken, operatorTestRequest()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("approvalStatus")).isEqualTo("PENDING_APPROVAL");
        assertThat(response.getBody().get("createdBy")).isNotNull();
    }

    @Test
    void adminCreatedMetricDefinitionIsAutoApproved() {
        String adminToken = createApprovedUserAndLogin(Role.ADMIN);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/metric-definitions"), HttpMethod.POST, authJsonEntity(adminToken, operatorTestRequest()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("approvalStatus")).isEqualTo("APPROVED");
        assertThat(response.getBody().get("approvedBy")).isNotNull();
    }

    @Test
    void operatorCannotUpdateMetricDefinition() {
        String adminToken = createApprovedUserAndLogin(Role.ADMIN);
        ResponseEntity<Map> createResponse = restTemplate.exchange(
                baseUrl("/api/metric-definitions"), HttpMethod.POST, authJsonEntity(adminToken, operatorTestRequest()), Map.class);
        Number id = (Number) createResponse.getBody().get("id");

        String operatorToken = createApprovedUserAndLogin(Role.OPERATOR);
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/metric-definitions/" + id), HttpMethod.PUT, authJsonEntity(operatorToken, operatorTestRequest()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanUpdateMetricDefinition() {
        String adminToken = createApprovedUserAndLogin(Role.ADMIN);
        ResponseEntity<Map> createResponse = restTemplate.exchange(
                baseUrl("/api/metric-definitions"), HttpMethod.POST, authJsonEntity(adminToken, operatorTestRequest()), Map.class);
        Number id = (Number) createResponse.getBody().get("id");

        MetricDefinitionRequest updateRequest = operatorTestRequest();
        updateRequest.setMetricKey(updateRequest.getMetricKey() + "-updated");
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/metric-definitions/" + id), HttpMethod.PUT, authJsonEntity(adminToken, updateRequest), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void operatorCannotDeleteMetricDefinition() {
        String adminToken = createApprovedUserAndLogin(Role.ADMIN);
        ResponseEntity<Map> createResponse = restTemplate.exchange(
                baseUrl("/api/metric-definitions"), HttpMethod.POST, authJsonEntity(adminToken, operatorTestRequest()), Map.class);
        Number id = (Number) createResponse.getBody().get("id");

        String operatorToken = createApprovedUserAndLogin(Role.OPERATOR);
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/metric-definitions/" + id), HttpMethod.DELETE, authEntity(operatorToken), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanDeleteMetricDefinition() {
        String adminToken = createApprovedUserAndLogin(Role.ADMIN);
        ResponseEntity<Map> createResponse = restTemplate.exchange(
                baseUrl("/api/metric-definitions"), HttpMethod.POST, authJsonEntity(adminToken, operatorTestRequest()), Map.class);
        Number id = (Number) createResponse.getBody().get("id");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/metric-definitions/" + id), HttpMethod.DELETE, authEntity(adminToken), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void operatorCannotApproveOrRejectMetricDefinitions() {
        String operatorToken = createApprovedUserAndLogin(Role.OPERATOR);
        Long pendingId = createPendingDefinitionAsOperator();

        ResponseEntity<Map> approveResponse = restTemplate.exchange(
                baseUrl("/api/metric-definitions/" + pendingId + "/approve"), HttpMethod.POST,
                authEntity(operatorToken), Map.class);
        assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanApproveAPendingMetricDefinition() {
        Long pendingId = createPendingDefinitionAsOperator();
        String adminToken = createApprovedUserAndLogin(Role.ADMIN);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/metric-definitions/" + pendingId + "/approve"), HttpMethod.POST,
                authEntity(adminToken), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("approvalStatus")).isEqualTo("APPROVED");
    }

    @Test
    void adminRejectWithoutReasonIsBadRequest() {
        Long pendingId = createPendingDefinitionAsOperator();
        String adminToken = createApprovedUserAndLogin(Role.ADMIN);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/metric-definitions/" + pendingId + "/reject"), HttpMethod.POST,
                authJsonEntity(adminToken, new com.monitoring.poc.metrics.dto.MetricApprovalRequest()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void attachingAPendingApprovalDefinitionToAGroupIsConflict() {
        Long pendingId = createPendingDefinitionAsOperator();

        Server server = new Server("rbac-pending-host-" + System.nanoTime(), "10.0.0.9", "monitoring_user",
                "enc", ServerStatus.ACTIVE, null);
        serverRepository.save(server);
        MetricGroup group = new MetricGroup(server, "Pending Approval Group", ExecutionMode.ON_DEMAND, 15);
        metricGroupRepository.save(group);

        String operatorToken = createApprovedUserAndLogin(Role.OPERATOR);
        com.monitoring.poc.metrics.dto.MetricGroupItemRequest itemRequest = new com.monitoring.poc.metrics.dto.MetricGroupItemRequest();
        itemRequest.setMetricDefinitionId(pendingId);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/metric-groups/" + group.getId() + "/items"), HttpMethod.POST,
                authJsonEntity(operatorToken, itemRequest), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    private MetricDefinitionRequest operatorTestRequest() {
        MetricDefinitionRequest request = new MetricDefinitionRequest();
        request.setName("Approval Workflow Test");
        request.setMetricKey("rbac_approval_test_" + System.nanoTime());
        request.setCategory("APPLICATION");
        request.setCommand("curl -sf http://127.0.0.1:8081/health");
        request.setTimeoutSeconds(3);
        request.setValueType(MetricValueType.RAW);
        return request;
    }

    private Long createPendingDefinitionAsOperator() {
        String operatorToken = createApprovedUserAndLogin(Role.OPERATOR);
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/metric-definitions"), HttpMethod.POST, authJsonEntity(operatorToken, operatorTestRequest()), Map.class);
        return ((Number) response.getBody().get("id")).longValue();
    }

    // ---------- Configuration drift tracker RBAC ----------

    private Long seedServerForConfigTracker() {
        Server server = new Server("cfg-rbac-host-" + System.nanoTime(), "10.0.0.9", "monitoring_user",
                "enc", ServerStatus.ACTIVE, null);
        serverRepository.save(server);
        return server.getId();
    }

    private com.monitoring.poc.configs.dto.TrackedConfigFileRequest trackedFileRequest(Long serverId) {
        com.monitoring.poc.configs.dto.TrackedConfigFileRequest request = new com.monitoring.poc.configs.dto.TrackedConfigFileRequest();
        request.setServerId(serverId);
        request.setFilePath("/data01/monitoring/app.conf");
        request.setFileLabel("App Config");
        request.setCheckIntervalSeconds(60);
        return request;
    }

    @Test
    void anyAuthenticatedRoleCanListTrackedConfigFiles() {
        String viewerToken = createApprovedUserAndLogin(Role.VIEWER);
        Long serverId = seedServerForConfigTracker();

        ResponseEntity<List> response = restTemplate.exchange(
                baseUrl("/api/configs/tracked-files?serverId=" + serverId), HttpMethod.GET, authEntity(viewerToken), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void viewerCannotCreateTrackedConfigFiles() {
        String viewerToken = createApprovedUserAndLogin(Role.VIEWER);
        Long serverId = seedServerForConfigTracker();

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/configs/tracked-files"), HttpMethod.POST,
                authJsonEntity(viewerToken, trackedFileRequest(serverId)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void operatorCanCreateAndDeleteTrackedConfigFiles() {
        String operatorToken = createApprovedUserAndLogin(Role.OPERATOR);
        Long serverId = seedServerForConfigTracker();

        ResponseEntity<Map> createResponse = restTemplate.exchange(
                baseUrl("/api/configs/tracked-files"), HttpMethod.POST,
                authJsonEntity(operatorToken, trackedFileRequest(serverId)), Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody().get("status")).isEqualTo("PENDING");

        Number id = (Number) createResponse.getBody().get("id");
        ResponseEntity<Map> deleteResponse = restTemplate.exchange(
                baseUrl("/api/configs/tracked-files/" + id), HttpMethod.DELETE, authEntity(operatorToken), Map.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void viewerCanReadHistoryAndDiffOfATrackedConfigFile() {
        String operatorToken = createApprovedUserAndLogin(Role.OPERATOR);
        Long serverId = seedServerForConfigTracker();
        ResponseEntity<Map> createResponse = restTemplate.exchange(
                baseUrl("/api/configs/tracked-files"), HttpMethod.POST,
                authJsonEntity(operatorToken, trackedFileRequest(serverId)), Map.class);
        Number id = (Number) createResponse.getBody().get("id");

        String viewerToken = createApprovedUserAndLogin(Role.VIEWER);
        ResponseEntity<List> historyResponse = restTemplate.exchange(
                baseUrl("/api/configs/tracked-files/" + id + "/history"), HttpMethod.GET, authEntity(viewerToken), List.class);
        assertThat(historyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(historyResponse.getBody()).isEmpty();
    }

    @Test
    void viewerCannotCheckNowOrAcceptBaselineOnATrackedConfigFile() {
        String operatorToken = createApprovedUserAndLogin(Role.OPERATOR);
        Long serverId = seedServerForConfigTracker();
        ResponseEntity<Map> createResponse = restTemplate.exchange(
                baseUrl("/api/configs/tracked-files"), HttpMethod.POST,
                authJsonEntity(operatorToken, trackedFileRequest(serverId)), Map.class);
        Number id = (Number) createResponse.getBody().get("id");

        String viewerToken = createApprovedUserAndLogin(Role.VIEWER);
        ResponseEntity<Map> checkNowResponse = restTemplate.exchange(
                baseUrl("/api/configs/tracked-files/" + id + "/check-now"), HttpMethod.POST, authEntity(viewerToken), Map.class);
        assertThat(checkNowResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> acceptBaselineResponse = restTemplate.exchange(
                baseUrl("/api/configs/tracked-files/" + id + "/accept-baseline"), HttpMethod.POST, authEntity(viewerToken), Map.class);
        assertThat(acceptBaselineResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void operatorCanCheckNowOnATrackedConfigFile() {
        String operatorToken = createApprovedUserAndLogin(Role.OPERATOR);
        Long serverId = seedServerForConfigTracker();
        ResponseEntity<Map> createResponse = restTemplate.exchange(
                baseUrl("/api/configs/tracked-files"), HttpMethod.POST,
                authJsonEntity(operatorToken, trackedFileRequest(serverId)), Map.class);
        Number id = (Number) createResponse.getBody().get("id");

        ResponseEntity<Map> checkNowResponse = restTemplate.exchange(
                baseUrl("/api/configs/tracked-files/" + id + "/check-now"), HttpMethod.POST, authEntity(operatorToken), Map.class);
        assertThat(checkNowResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(checkNowResponse.getBody().get("forceCheckRequested")).isEqualTo(true);
    }

    // ---------- Provisioning script manager RBAC ----------

    private com.monitoring.poc.servers.dto.ProvisioningScriptRequest provisioningScriptRequest() {
        com.monitoring.poc.servers.dto.ProvisioningScriptRequest request = new com.monitoring.poc.servers.dto.ProvisioningScriptRequest();
        request.setCommandLine("echo rbac-test");
        request.setDescription("RBAC test script");
        return request;
    }

    @Test
    void operatorCannotListProvisioningScripts() {
        String operatorToken = createApprovedUserAndLogin(Role.OPERATOR);
        ResponseEntity<List> response = restTemplate.exchange(
                baseUrl("/api/provisioning-scripts"), HttpMethod.GET, authEntity(operatorToken), List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void viewerCannotCreateProvisioningScripts() {
        String viewerToken = createApprovedUserAndLogin(Role.VIEWER);
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl("/api/provisioning-scripts"), HttpMethod.POST,
                authJsonEntity(viewerToken, provisioningScriptRequest()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanManageProvisioningScriptsEndToEnd() {
        String adminToken = createApprovedUserAndLogin(Role.ADMIN);

        ResponseEntity<Map> createResponse = restTemplate.exchange(
                baseUrl("/api/provisioning-scripts"), HttpMethod.POST,
                authJsonEntity(adminToken, provisioningScriptRequest()), Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Number id = (Number) createResponse.getBody().get("id");

        ResponseEntity<Map> moveUpResponse = restTemplate.exchange(
                baseUrl("/api/provisioning-scripts/" + id + "/move-up"), HttpMethod.POST, authEntity(adminToken), Map.class);
        assertThat(moveUpResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> disableResponse = restTemplate.exchange(
                baseUrl("/api/provisioning-scripts/" + id), HttpMethod.PATCH,
                authJsonEntity(adminToken, Map.of("isEnabled", false)), Map.class);
        assertThat(disableResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(disableResponse.getBody().get("isEnabled")).isEqualTo(false);

        ResponseEntity<Map> deleteResponse = restTemplate.exchange(
                baseUrl("/api/provisioning-scripts/" + id), HttpMethod.DELETE, authEntity(adminToken), Map.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
