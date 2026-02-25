package com.purelearning.smart_meter.dto.auth;

public class WechatLoginResponse {

    private String token;
    private long expiresInSeconds;
    private boolean newUser;
    private AuthUserView user;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(long expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }

    public boolean isNewUser() {
        return newUser;
    }

    public void setNewUser(boolean newUser) {
        this.newUser = newUser;
    }

    public AuthUserView getUser() {
        return user;
    }

    public void setUser(AuthUserView user) {
        this.user = user;
    }
}

