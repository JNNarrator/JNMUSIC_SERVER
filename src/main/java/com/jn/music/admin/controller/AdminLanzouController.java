package com.jn.music.admin.controller;

import com.jn.music.common.ApiResponse;
import com.jn.music.common.enums.ErrorCode;
import com.jn.music.common.exception.BusinessException;
import com.jn.music.lanzou.LanzouApiClient;
import com.jn.music.lanzou.LanzouSessionException;
import com.jn.music.lanzou.LanzouUidVei;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 后台管理：蓝奏云会话入口。
 * 提供两种更新方式：
 *   1) POST /cookie 直接粘贴浏览器 Cookie
 *   2) POST /login  账号密码自动登录
 * 均会立即用 getUidVei 探活，并把结果写入本地 Cookie 缓存。
 */
@RestController
@RequestMapping("/api/v1/admin/lanzou")
public class AdminLanzouController {

    private final LanzouApiClient lanzouClient;

    public AdminLanzouController(LanzouApiClient lanzouClient) {
        this.lanzouClient = lanzouClient;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            LanzouUidVei uv = lanzouClient.getUidVei();
            data.put("authenticated", true);
            data.put("uid", uv.uid());
        } catch (RuntimeException e) {
            data.put("authenticated", false);
            data.put("reason", e.getMessage());
        }
        return ApiResponse.success(data);
    }

    @PostMapping("/cookie")
    public ApiResponse<Map<String, Object>> updateCookie(@Valid @RequestBody CookieRequest req) {
        try {
            lanzouClient.setSessionCookie(req.cookie().trim());
            lanzouClient.saveCookieCache();
            LanzouUidVei uv = lanzouClient.getUidVei();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("authenticated", true);
            data.put("uid", uv.uid());
            return ApiResponse.success(data);
        } catch (LanzouSessionException e) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Cookie 无效: " + e.getMessage());
        }
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> loginByPassword(@Valid @RequestBody LoginRequest req) {
        try {
            lanzouClient.login(req.username().trim(), req.password());
            lanzouClient.saveCookieCache();
            LanzouUidVei uv = lanzouClient.getUidVei();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("authenticated", true);
            data.put("uid", uv.uid());
            return ApiResponse.success(data);
        } catch (LanzouSessionException e) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "登录失败: " + e.getMessage());
        }
    }

    public record CookieRequest(@NotBlank String cookie) {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
}
