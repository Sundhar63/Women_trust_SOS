package com.foxsite.womentrust.api_services;

public class AuthRefreshRequest {
    public String refresh_token;
    public String device_id;

    public AuthRefreshRequest(String refreshToken, String deviceId) {
        this.refresh_token = refreshToken;
        this.device_id = deviceId;
    }
}
