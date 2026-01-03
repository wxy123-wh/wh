package com.wh.reputation.auth;

import com.wh.reputation.common.BadRequestException;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {
    private static final Map<String, UserDef> USERS = Map.of(
            "pm", new UserDef("123456", "PM"),
            "market", new UserDef("123456", "MARKET"),
            "ops", new UserDef("123456", "OPS")
    );

    private final ConcurrentHashMap<String, String> tokenRoleMap = new ConcurrentHashMap<>();

    public LoginResponseDto login(String usernameRaw, String passwordRaw) {
        String username = usernameRaw == null ? "" : usernameRaw.trim().toLowerCase(Locale.ROOT);
        String password = passwordRaw == null ? "" : passwordRaw;
        UserDef def = USERS.get(username);
        if (def == null || !def.password().equals(password)) {
            throw new BadRequestException("invalid username or password");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        tokenRoleMap.put(token, def.role());
        return new LoginResponseDto(token, def.role());
    }

    public boolean isValidToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return tokenRoleMap.containsKey(token.trim());
    }

    public String roleOf(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return tokenRoleMap.get(token.trim());
    }

    private record UserDef(String password, String role) {}
}

