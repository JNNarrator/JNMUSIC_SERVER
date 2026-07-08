package com.jn.music.admin.controller;

import com.jn.music.admin.service.AdminTokenStore;
import com.jn.music.common.exception.GlobalExceptionHandler;
import com.jn.music.lanzou.LanzouApiClient;
import com.jn.music.lanzou.LanzouSessionException;
import com.jn.music.lanzou.LanzouUidVei;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 蓝奏云 Cookie / 账密后台入口的行为契约。
 */
class AdminLanzouControllerTest {

    private LanzouApiClient lanzouClient;
    private AdminTokenStore tokenStore;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        lanzouClient = mock(LanzouApiClient.class);
        tokenStore = mock(AdminTokenStore.class);
        when(tokenStore.isValid("valid-token", "jiangnan")).thenReturn(true);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminLanzouController(lanzouClient, tokenStore))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void status_shouldReturnAuthenticatedWhenClientHasSession() throws Exception {
        when(lanzouClient.getUidVei()).thenReturn(new LanzouUidVei("5132788", "vei-xyz"));

        mockMvc.perform(get("/api/v1/admin/lanzou/status")
                        .header("X-Admin-Token", "valid-token")
                        .header("X-Admin-User", "jiangnan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.uid").value("5132788"));
    }

    @Test
    void status_shouldReturnUnauthenticatedWhenSessionMissing() throws Exception {
        when(lanzouClient.getUidVei()).thenThrow(new LanzouSessionException("no uid"));

        mockMvc.perform(get("/api/v1/admin/lanzou/status")
                        .header("X-Admin-Token", "valid-token")
                        .header("X-Admin-User", "jiangnan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(false));
    }

    @Test
    void pasteCookie_shouldPersistAndProbe() throws Exception {
        when(lanzouClient.getUidVei()).thenReturn(new LanzouUidVei("5132788", "vei-xyz"));

        mockMvc.perform(post("/api/v1/admin/lanzou/cookie")
                        .header("X-Admin-Token", "valid-token")
                        .header("X-Admin-User", "jiangnan")
                        .contentType(APPLICATION_JSON)
                        .content("{\"cookie\":\"PHPSESSID=x; phpdisk_info=y\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.uid").value("5132788"));

        verify(lanzouClient).setSessionCookie("PHPSESSID=x; phpdisk_info=y");
        verify(lanzouClient).saveCookieCache();
        verify(lanzouClient).getUidVei();
    }

    @Test
    void pasteCookie_shouldReportFriendlyErrorOnInvalidCookie() throws Exception {
        doThrow(new LanzouSessionException("missing cookie: phpdisk_info"))
                .when(lanzouClient).setSessionCookie(any());

        mockMvc.perform(post("/api/v1/admin/lanzou/cookie")
                        .header("X-Admin-Token", "valid-token")
                        .header("X-Admin-User", "jiangnan")
                        .contentType(APPLICATION_JSON)
                        .content("{\"cookie\":\"bogus\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void loginByPassword_shouldCallLoginAndSave() throws Exception {
        when(lanzouClient.getUidVei()).thenReturn(new LanzouUidVei("5132788", "v"));

        mockMvc.perform(post("/api/v1/admin/lanzou/login")
                        .header("X-Admin-Token", "valid-token")
                        .header("X-Admin-User", "jiangnan")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"13949121576\",\"password\":\"pwd\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.uid").value("5132788"));

        verify(lanzouClient).login("13949121576", "pwd");
        verify(lanzouClient).saveCookieCache();
    }

    @Test
    void loginByPassword_shouldRejectMissingFields() throws Exception {
        mockMvc.perform(post("/api/v1/admin/lanzou/login")
                        .header("X-Admin-Token", "valid-token")
                        .header("X-Admin-User", "jiangnan")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthorizedRequestShouldFail() throws Exception {
        mockMvc.perform(get("/api/v1/admin/lanzou/status"))
                .andExpect(status().isUnauthorized());
    }
}
