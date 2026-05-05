package com.purelearning.smart_meter.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 管理端用户保存请求。
 *
 * @param username  登录账号，新增时必填
 * @param password  明文密码，新增时必填；更新时为空则不修改密码
 * @param openid    微信 openid，可选
 * @param nickname  用户昵称
 * @param avatarUrl 头像地址
 * @param status    用户状态：1 正常，0 禁用
 * @param userType  用户类型：1 普通用户，2 管理员
 */
@Schema(description = "管理端用户保存请求")
public record AdminUserRequest(
        String username,
        String password,
        String openid,
        String nickname,
        String avatarUrl,
        Integer status,
        Integer userType
) {
}
