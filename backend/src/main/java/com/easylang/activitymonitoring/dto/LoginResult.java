package com.easylang.activitymonitoring.dto;

import com.easylang.activitymonitoring.dto.AuthDtos.AuthResponse;
import java.time.Duration;

public record LoginResult(String token, Duration tokenTtl, AuthResponse response) {
}
