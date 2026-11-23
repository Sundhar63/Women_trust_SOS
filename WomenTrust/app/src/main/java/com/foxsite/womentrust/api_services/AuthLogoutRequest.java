package com.foxsite.womentrust.api_services;

public class AuthLogoutRequest {
    public String refresh_token;

    public AuthLogoutRequest(String refreshToken) {
        this.refresh_token = refreshToken;
    }
}
