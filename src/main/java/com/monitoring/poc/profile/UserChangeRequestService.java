package com.monitoring.poc.profile;

import com.monitoring.poc.email.EmailService;
import com.monitoring.poc.entity.User;
import com.monitoring.poc.entity.UserChangeRequest;
import com.monitoring.poc.enums.ApprovalStatus;
import com.monitoring.poc.enums.NotificationType;
import com.monitoring.poc.enums.Role;
import com.monitoring.poc.enums.UserChangeType;
import com.monitoring.poc.exception.ApiException;
import com.monitoring.poc.notifications.NotificationService;
import com.monitoring.poc.profile.dto.EmailChangeRequest;
import com.monitoring.poc.profile.dto.PasswordChangeRequest;
import com.monitoring.poc.profile.dto.ProfileResponse;
import com.monitoring.poc.profile.dto.UserChangeRequestResponse;
import com.monitoring.poc.repository.UserChangeRequestRepository;
import com.monitoring.poc.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserChangeRequestService {

    private final UserRepository userRepository;
    private final UserChangeRequestRepository changeRequestRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final NotificationService notificationService;

    public UserChangeRequestService(UserRepository userRepository,
                                     UserChangeRequestRepository changeRequestRepository,
                                     PasswordEncoder passwordEncoder,
                                     EmailService emailService,
                                     NotificationService notificationService) {
        this.userRepository = userRepository;
        this.changeRequestRepository = changeRequestRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.notificationService = notificationService;
    }

    public ProfileResponse getProfile(String username) {
        return new ProfileResponse(findUserOrThrow(username));
    }

    @Transactional
    public UserChangeRequestResponse requestEmailChange(String username, EmailChangeRequest request) {
        User user = findUserOrThrow(username);
        verifyCurrentPassword(user, request.getCurrentPassword());
        requireNoPendingRequest(user);

        if (userRepository.existsByEmail(request.getNewEmail())) {
            throw new ApiException(HttpStatus.CONFLICT, "Bu e-posta adresi zaten kullaniliyor");
        }

        UserChangeRequest changeRequest = new UserChangeRequest(user, UserChangeType.EMAIL, request.getNewEmail(), null);
        changeRequestRepository.save(changeRequest);
        notifyAdminsOfRequest(username, "e-posta");
        return new UserChangeRequestResponse(changeRequest);
    }

    @Transactional
    public UserChangeRequestResponse requestPasswordChange(String username, PasswordChangeRequest request) {
        User user = findUserOrThrow(username);
        verifyCurrentPassword(user, request.getCurrentPassword());
        requireNoPendingRequest(user);

        String newPasswordHash = passwordEncoder.encode(request.getNewPassword());
        UserChangeRequest changeRequest = new UserChangeRequest(user, UserChangeType.PASSWORD, null, newPasswordHash);
        changeRequestRepository.save(changeRequest);
        notifyAdminsOfRequest(username, "sifre");
        return new UserChangeRequestResponse(changeRequest);
    }

    public List<UserChangeRequestResponse> listMine(String username) {
        User user = findUserOrThrow(username);
        return changeRequestRepository.findByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(UserChangeRequestResponse::new)
                .toList();
    }

    public List<UserChangeRequestResponse> listPending() {
        return changeRequestRepository.findByStatusOrderByCreatedAtAsc(ApprovalStatus.PENDING_APPROVAL).stream()
                .map(UserChangeRequestResponse::new)
                .toList();
    }

    @Transactional
    public UserChangeRequestResponse approve(Long id, String adminUsername) {
        UserChangeRequest request = findRequestOrThrow(id);
        requirePending(request);

        User user = request.getUser();
        String changeTypeLabel;
        if (request.getRequestType() == UserChangeType.EMAIL) {
            user.setEmail(request.getNewEmail());
            changeTypeLabel = "e-posta";
        } else {
            user.setPasswordHash(request.getNewPasswordHash());
            changeTypeLabel = "sifre";
        }
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        request.setStatus(ApprovalStatus.APPROVED);
        request.setReviewedBy(adminUsername);
        request.setReviewedAt(LocalDateTime.now());
        changeRequestRepository.save(request);

        emailService.sendProfileChangeApprovedEmail(user.getEmail(), changeTypeLabel);
        notificationService.notify(user.getUsername(), NotificationType.PROFILE_CHANGE_APPROVED,
                "Profil Degisikligi Onaylandi",
                changeTypeLabel + " degisiklik talebiniz onaylandi.", "profile.html");

        return new UserChangeRequestResponse(request);
    }

    @Transactional
    public UserChangeRequestResponse reject(Long id, String adminUsername, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Reddetme gerekcesi (rejectionReason) zorunludur");
        }
        UserChangeRequest request = findRequestOrThrow(id);
        requirePending(request);

        request.setStatus(ApprovalStatus.REJECTED);
        request.setReviewedBy(adminUsername);
        request.setRejectionReason(reason);
        request.setReviewedAt(LocalDateTime.now());
        changeRequestRepository.save(request);

        String changeTypeLabel = request.getRequestType() == UserChangeType.EMAIL ? "e-posta" : "sifre";
        User user = request.getUser();
        emailService.sendProfileChangeRejectedEmail(user.getEmail(), changeTypeLabel, reason);
        notificationService.notify(user.getUsername(), NotificationType.PROFILE_CHANGE_REJECTED,
                "Profil Degisikligi Reddedildi",
                changeTypeLabel + " degisiklik talebiniz reddedildi. Gerekce: " + reason, "profile.html");

        return new UserChangeRequestResponse(request);
    }

    private void notifyAdminsOfRequest(String requestingUsername, String changeTypeLabel) {
        List<String> adminEmails = userRepository.findByRole(Role.ADMIN).stream().map(User::getEmail).toList();
        emailService.sendProfileChangeRequestEmail(adminEmails, requestingUsername, changeTypeLabel);
        notificationService.notifyAdmins(NotificationType.PROFILE_CHANGE_REQUEST,
                "Yeni Profil Degisiklik Talebi",
                requestingUsername + " kullanicisi " + changeTypeLabel + " degisikligi talep etti.",
                "admin.html");
    }

    private void verifyCurrentPassword(User user, String currentPassword) {
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Mevcut sifre hatali");
        }
    }

    private void requireNoPendingRequest(User user) {
        if (changeRequestRepository.existsByUser_IdAndStatus(user.getId(), ApprovalStatus.PENDING_APPROVAL)) {
            throw new ApiException(HttpStatus.CONFLICT, "Zaten onay bekleyen bir degisiklik talebiniz var");
        }
    }

    private void requirePending(UserChangeRequest request) {
        if (request.getStatus() != ApprovalStatus.PENDING_APPROVAL) {
            throw new ApiException(HttpStatus.CONFLICT, "Bu talep zaten degerlendirilmis");
        }
    }

    private User findUserOrThrow(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Kullanici bulunamadi"));
    }

    private UserChangeRequest findRequestOrThrow(Long id) {
        return changeRequestRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Degisiklik talebi bulunamadi"));
    }
}
