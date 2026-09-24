package com.easylang.activitymonitoring.service;

import com.easylang.activitymonitoring.dto.AuthDtos.AuthResponse;
import com.easylang.activitymonitoring.dto.AuthDtos.LoginRequest;
import com.easylang.activitymonitoring.dto.AuthDtos.UserResponse;
import com.easylang.activitymonitoring.dto.LoginResult;
import com.easylang.activitymonitoring.security.AuthenticatedUser;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public LoginResult login(LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email().trim(), request.password())
        );
        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        return new LoginResult(
                jwtService.createToken(principal),
                jwtService.tokenTtl(),
                new AuthResponse(UserResponse.from(principal.user()))
        );
    }
}
