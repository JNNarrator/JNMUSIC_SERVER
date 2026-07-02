package com.jn.music.admin.controller;

import com.jn.music.admin.dto.AdminLoginRequest;
import com.jn.music.admin.dto.AdminLoginResponse;
import com.jn.music.common.ApiResponse;
import com.jn.music.admin.service.AdminTokenStore;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {

    private final AdminTokenStore tokenStore;

    public AdminAuthController(AdminTokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @PostMapping("/login")
    public ApiResponse<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        if (!"jiangnan".equals(request.getUsername()) || !"jiangnan123".equals(request.getPassword())) {
            throw new IllegalArgumentException("账号或密码错误");
        }
        return ApiResponse.success(AdminLoginResponse.builder()
                .token(tokenStore.issueToken(request.getUsername()))
                .username(request.getUsername())
                .build());
    }
}
