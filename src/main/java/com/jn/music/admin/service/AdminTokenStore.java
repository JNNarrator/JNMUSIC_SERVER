package com.jn.music.admin.service;

import com.jn.music.common.TraceIdContext;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AdminTokenStore {

    private static final Logger log = LoggerFactory.getLogger(AdminTokenStore.class);
    private final Map<String, String> tokenToUsername = new ConcurrentHashMap<>();
    private final Map<String, String> usernameToToken = new ConcurrentHashMap<>();

    public String issueToken(String username) {
        String oldToken = usernameToToken.get(username);
        if (oldToken != null) {
            tokenToUsername.remove(oldToken);
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        tokenToUsername.put(token, username);
        usernameToToken.put(username, token);
        log.info("发放管理后台 token traceId={} username={}", TraceIdContext.getTraceId(), username);
        return token;
    }

    public boolean isValid(String token, String username) {
        return username.equals(tokenToUsername.get(token));
    }
}
