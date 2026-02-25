package com.purelearning.smart_meter.service;

import com.purelearning.smart_meter.dto.auth.WechatLoginRequest;
import com.purelearning.smart_meter.dto.auth.WechatLoginResponse;
import com.purelearning.smart_meter.security.JwtService;

public interface AuthService {

    WechatLoginResponse wechatLogin(WechatLoginRequest request);

    /**
     * 开发阶段假登录：不调用微信 jscode2session，使用模拟 openid 完成与真实登录一致的查/建用户、签发 JWT 流程。
     * 仅用于联调与本地验证，生产环境应禁用或移除。
     *
     * @param request 与真实登录相同，code 作为开发者标识（如 dev_001），用于生成 mock_openid_xxx
     * @return 与 wechatLogin 相同的响应结构
     */
    WechatLoginResponse wechatLoginMock(WechatLoginRequest request);

    JwtService.VerifiedJwt verifyToken(String token);
}

