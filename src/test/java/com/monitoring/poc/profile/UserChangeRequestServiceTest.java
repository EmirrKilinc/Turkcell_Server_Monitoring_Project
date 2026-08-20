package com.monitoring.poc.profile;

import com.monitoring.poc.email.EmailService;
import com.monitoring.poc.entity.User;
import com.monitoring.poc.entity.UserChangeRequest;
import com.monitoring.poc.enums.ApprovalStatus;
import com.monitoring.poc.enums.Role;
import com.monitoring.poc.enums.UserChangeType;
import com.monitoring.poc.enums.UserStatus;
import com.monitoring.poc.exception.ApiException;
import com.monitoring.poc.notifications.NotificationService;
import com.monitoring.poc.profile.dto.EmailChangeRequest;
import com.monitoring.poc.profile.dto.PasswordChangeRequest;
import com.monitoring.poc.profile.dto.UserChangeRequestResponse;
import com.monitoring.poc.repository.UserChangeRequestRepository;
import com.monitoring.poc.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserChangeRequestServiceTest {

    private UserRepository userRepository;
    private UserChangeRequestRepository changeRequestRepository;
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private EmailService emailService;
    private NotificationService notificationService;
    private UserChangeRequestService service;

    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        changeRequestRepository = mock(UserChangeRequestRepository.class);
        passwordEncoder = mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        emailService = mock(EmailService.class);
        notificationService = mock(NotificationService.class);
        service = new UserChangeRequestService(userRepository, changeRequestRepository, passwordEncoder,
                emailService, notificationService);

        user = new User("op1", "op1@example.com", "hashed-current", Role.OPERATOR, UserStatus.APPROVED);
        user.setId(1L);
        when(userRepository.findByUsername("op1")).thenReturn(Optional.of(user));
    }

    private EmailChangeRequest emailChangeRequest(String currentPassword, String newEmail) {
        EmailChangeRequest req = new EmailChangeRequest();
        req.setCurrentPassword(currentPassword);
        req.setNewEmail(newEmail);
        return req;
    }

    private PasswordChangeRequest passwordChangeRequest(String currentPassword, String newPassword) {
        PasswordChangeRequest req = new PasswordChangeRequest();
        req.setCurrentPassword(currentPassword);
        req.setNewPassword(newPassword);
        return req;
    }

    @Test
    void requestEmailChangeRejectsWrongCurrentPassword() {
        when(passwordEncoder.matches("wrong", "hashed-current")).thenReturn(false);

        assertThatThrownBy(() -> service.requestEmailChange("op1", emailChangeRequest("wrong", "new@example.com")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void requestEmailChangeRejectsWhenEmailAlreadyInUse() {
        when(passwordEncoder.matches("correct", "hashed-current")).thenReturn(true);
        when(changeRequestRepository.existsByUser_IdAndStatus(1L, ApprovalStatus.PENDING_APPROVAL)).thenReturn(false);
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.requestEmailChange("op1", emailChangeRequest("correct", "taken@example.com")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void requestEmailChangeRejectsWhenAPendingRequestAlreadyExists() {
        when(passwordEncoder.matches("correct", "hashed-current")).thenReturn(true);
        when(changeRequestRepository.existsByUser_IdAndStatus(1L, ApprovalStatus.PENDING_APPROVAL)).thenReturn(true);

        assertThatThrownBy(() -> service.requestEmailChange("op1", emailChangeRequest("correct", "new@example.com")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void requestEmailChangeSavesAPendingRequestAndNotifiesAdmins() {
        when(passwordEncoder.matches("correct", "hashed-current")).thenReturn(true);
        when(changeRequestRepository.existsByUser_IdAndStatus(1L, ApprovalStatus.PENDING_APPROVAL)).thenReturn(false);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.findByRole(Role.ADMIN)).thenReturn(java.util.List.of());

        UserChangeRequestResponse response = service.requestEmailChange("op1", emailChangeRequest("correct", "new@example.com"));

        assertThat(response.getRequestType()).isEqualTo("EMAIL");
        assertThat(response.getNewEmail()).isEqualTo("new@example.com");
        assertThat(response.getStatus()).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void requestPasswordChangeHashesTheNewPasswordImmediately() {
        when(passwordEncoder.matches("correct", "hashed-current")).thenReturn(true);
        when(changeRequestRepository.existsByUser_IdAndStatus(1L, ApprovalStatus.PENDING_APPROVAL)).thenReturn(false);
        when(passwordEncoder.encode("NewPassword123!")).thenReturn("hashed-new");
        when(userRepository.findByRole(Role.ADMIN)).thenReturn(java.util.List.of());

        service.requestPasswordChange("op1", passwordChangeRequest("correct", "NewPassword123!"));

        org.mockito.ArgumentCaptor<UserChangeRequest> captor = org.mockito.ArgumentCaptor.forClass(UserChangeRequest.class);
        org.mockito.Mockito.verify(changeRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getNewPasswordHash()).isEqualTo("hashed-new");
    }

    @Test
    void approveAppliesEmailChangeToTheUser() {
        UserChangeRequest request = new UserChangeRequest(user, UserChangeType.EMAIL, "new@example.com", null);
        request.setId(10L);
        when(changeRequestRepository.findById(10L)).thenReturn(Optional.of(request));

        service.approve(10L, "admin1");

        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(request.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(request.getReviewedBy()).isEqualTo("admin1");
    }

    @Test
    void approveAppliesPasswordChangeToTheUser() {
        UserChangeRequest request = new UserChangeRequest(user, UserChangeType.PASSWORD, null, "hashed-new");
        request.setId(11L);
        when(changeRequestRepository.findById(11L)).thenReturn(Optional.of(request));

        service.approve(11L, "admin1");

        assertThat(user.getPasswordHash()).isEqualTo("hashed-new");
    }

    @Test
    void approveOfAnAlreadyDecidedRequestIsConflict() {
        UserChangeRequest request = new UserChangeRequest(user, UserChangeType.EMAIL, "new@example.com", null);
        request.setId(12L);
        request.setStatus(ApprovalStatus.APPROVED);
        when(changeRequestRepository.findById(12L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.approve(12L, "admin1")).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectWithBlankReasonIsBadRequest() {
        assertThatThrownBy(() -> service.reject(1L, "admin1", "  ")).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectSetsReasonAndReviewerWithoutTouchingTheUser() {
        UserChangeRequest request = new UserChangeRequest(user, UserChangeType.EMAIL, "new@example.com", null);
        request.setId(13L);
        when(changeRequestRepository.findById(13L)).thenReturn(Optional.of(request));

        service.reject(13L, "admin1", "Supheli talep");

        assertThat(request.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(request.getRejectionReason()).isEqualTo("Supheli talep");
        assertThat(user.getEmail()).isEqualTo("op1@example.com");
    }
}
