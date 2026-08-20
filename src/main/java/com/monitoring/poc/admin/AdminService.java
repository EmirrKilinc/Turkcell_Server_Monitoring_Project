package com.monitoring.poc.admin;

import com.monitoring.poc.admin.dto.UserSummaryResponse;
import com.monitoring.poc.entity.User;
import com.monitoring.poc.enums.Role;
import com.monitoring.poc.enums.UserStatus;
import com.monitoring.poc.exception.ApiException;
import com.monitoring.poc.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminService {

    private final UserRepository userRepository;

    public AdminService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserSummaryResponse> listUsers(UserStatus statusFilter) {
        List<User> users = statusFilter != null
                ? userRepository.findByStatus(statusFilter)
                : userRepository.findAll();

        return users.stream().map(UserSummaryResponse::new).toList();
    }

    public UserSummaryResponse updateStatus(Long userId, UserStatus newStatus) {
        User user = getUserOrThrow(userId);
        user.setStatus(newStatus);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return new UserSummaryResponse(user);
    }

    public UserSummaryResponse updateRole(Long userId, Role newRole) {
        User user = getUserOrThrow(userId);
        user.setRole(newRole);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return new UserSummaryResponse(user);
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Kullanici bulunamadi"));
    }
}
