package com.purelearning.smart_meter.dto.user;

/**
 * 用户头像更新结果响应体。
 *
 * @param avatarUrl 更新后的头像 URL
 */
public class AvatarUpdateResponse {

    private String avatarUrl;

    public AvatarUpdateResponse() {
    }

    public AvatarUpdateResponse(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}

