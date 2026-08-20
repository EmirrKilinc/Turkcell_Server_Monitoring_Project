package com.monitoring.poc.profile;

import com.monitoring.poc.profile.dto.ChangeRequestRejection;
import com.monitoring.poc.profile.dto.EmailChangeRequest;
import com.monitoring.poc.profile.dto.PasswordChangeRequest;
import com.monitoring.poc.profile.dto.ProfileResponse;
import com.monitoring.poc.profile.dto.UserChangeRequestResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final UserChangeRequestService userChangeRequestService;

    public ProfileController(UserChangeRequestService userChangeRequestService) {
        this.userChangeRequestService = userChangeRequestService;
    }

    @GetMapping("/me")
    public ResponseEntity<ProfileResponse> me(Authentication authentication) {
        return ResponseEntity.ok(userChangeRequestService.getProfile(authentication.getName()));
    }

    @PostMapping("/change-requests/email")
    public ResponseEntity<UserChangeRequestResponse> requestEmailChange(@Valid @RequestBody EmailChangeRequest request,
                                                                          Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userChangeRequestService.requestEmailChange(authentication.getName(), request));
    }

    @PostMapping("/change-requests/password")
    public ResponseEntity<UserChangeRequestResponse> requestPasswordChange(@Valid @RequestBody PasswordChangeRequest request,
                                                                             Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userChangeRequestService.requestPasswordChange(authentication.getName(), request));
    }

    @GetMapping("/change-requests/mine")
    public ResponseEntity<List<UserChangeRequestResponse>> mine(Authentication authentication) {
        return ResponseEntity.ok(userChangeRequestService.listMine(authentication.getName()));
    }

    @GetMapping("/change-requests/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserChangeRequestResponse>> pending() {
        return ResponseEntity.ok(userChangeRequestService.listPending());
    }

    @PostMapping("/change-requests/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserChangeRequestResponse> approve(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(userChangeRequestService.approve(id, authentication.getName()));
    }

    @PostMapping("/change-requests/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserChangeRequestResponse> reject(@PathVariable Long id,
                                                              @RequestBody ChangeRequestRejection request,
                                                              Authentication authentication) {
        return ResponseEntity.ok(userChangeRequestService.reject(id, authentication.getName(), request.getRejectionReason()));
    }
}
