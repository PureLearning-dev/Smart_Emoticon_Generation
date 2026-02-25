package com.purelearning.smart_meter.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.purelearning.smart_meter.config.props.WechatMiniAppProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class WechatMiniAppClient {

    private final RestTemplate restTemplate;
    private final WechatMiniAppProperties props;

    public WechatMiniAppClient(RestTemplate restTemplate, WechatMiniAppProperties props) {
        this.restTemplate = restTemplate;
        this.props = props;
    }

    public WechatSession exchangeCode(String code) {
        if (!StringUtils.hasText(props.getAppid()) || !StringUtils.hasText(props.getSecret())) {
            throw new IllegalStateException("Missing wechat.miniapp.appid/secret in application.yaml or env vars");
        }
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("code must not be blank");
        }

        String url = UriComponentsBuilder
                .fromUriString("https://api.weixin.qq.com/sns/jscode2session")
                .queryParam("appid", props.getAppid())
                .queryParam("secret", props.getSecret())
                .queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code")
                .toUriString();

        WechatSessionResponse resp = restTemplate.getForObject(url, WechatSessionResponse.class);
        if (resp == null) {
            throw new IllegalStateException("Wechat jscode2session returned empty response");
        }
        if (resp.errcode != null && resp.errcode != 0) {
            throw new IllegalStateException("Wechat jscode2session error: errcode=" + resp.errcode + ", errmsg=" + resp.errmsg);
        }
        if (!StringUtils.hasText(resp.openid)) {
            throw new IllegalStateException("Wechat jscode2session missing openid");
        }

        return new WechatSession(resp.openid, resp.sessionKey, resp.unionid);
    }

    public record WechatSession(String openid, String sessionKey, String unionid) {
    }

    static class WechatSessionResponse {
        public String openid;

        @JsonProperty("session_key")
        public String sessionKey;

        public String unionid;

        public Integer errcode;

        public String errmsg;
    }
}

