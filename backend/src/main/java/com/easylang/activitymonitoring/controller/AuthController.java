package com.easylang.activitymonitoring.controller;

import com.easylang.activitymonitoring.dto.AuthDtos.AuthResponse;
import com.easylang.activitymonitoring.dto.AuthDtos.CsrfResponse;
import com.easylang.activitymonitoring.dto.AuthDtos.LoginRequest;
import com.easylang.activitymonitoring.dto.AuthDtos.UserResponse;
import com.easylang.activitymonitoring.dto.LoginResult;
import com.easylang.activitymonitoring.security.AuthenticatedUser;
import com.easylang.activitymonitoring.security.JwtAuthenticationFilter;
import com.easylang.activitymonitoring.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final boolean secureCookie;

    public AuthController(
            AuthService authService,
            @Value("${app.security.secure-cookie:false}") boolean secureCookie
    ) {
        this.authService = authService;
        this.secureCookie = secureCookie;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken csrfToken) {
        return new CsrfResponse(csrfToken.getToken(), csrfToken.getHeaderName());
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        LoginResult result = authService.login(request);
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie(result.token(), result.tokenTtl().toSeconds()).toString());
        return result.response();
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return UserResponse.from(principal.user());
    }

    @PostMapping("/logout")
    public void logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie("", 0).toString());
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    private ResponseCookie accessCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(JwtAuthenticationFilter.ACCESS_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }
}
