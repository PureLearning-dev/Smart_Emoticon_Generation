package com.purelearning.smart_meter.security;

/**
 * 当前登录用户信息（由 JWT 解析后写入 Spring Security 上下文）。
 *
 * @param userId   用户 ID（users 表主键）
 * @param openid   微信 openid（可能为空：取决于登录方式/历史数据）
 * @param userType 用户类型编码（见 {@code com.purelearning.smart_meter.service.enums.UserType}）
 */
public record CurrentUser(Long userId, String openid, Integer userType) {
}

