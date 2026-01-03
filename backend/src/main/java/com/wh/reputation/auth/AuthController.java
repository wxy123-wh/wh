package com.wh.reputation.auth;

import com.wh.reputation.common.ApiResponse;
import com.wh.reputation.common.BadRequestException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponseDto> login(@RequestBody LoginRequest body) {
        if (body == null) {
            throw new BadRequestException("body is required");
        }
        return ApiResponse.ok(authService.login(body.username(), body.password()));
    }
}

