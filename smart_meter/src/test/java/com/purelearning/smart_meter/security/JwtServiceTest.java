package com.purelearning.smart_meter.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.purelearning.smart_meter.config.props.JwtProperties;
import com.purelearning.smart_meter.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * JwtService 纯单元测试：
 * <p>
 * - 签发 token、校验 token、过期检测、非法输入拒绝。
 */
class JwtServiceTest {

    /**
     * 至少 32 字节的测试密钥。
     */
    private static final String TEST_SECRET = "bH8Kz9QvL3xT2pW6rY7nC4mF1uJ0kS5d";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(TEST_SECRET);
        props.setTtlSeconds(3600);
        jwtService = new JwtService(props, new ObjectMapper());
    }

    // ======================== 签发 ========================

    @Test
    @DisplayName("签发 - 正常用户：返回非空 Token")
    void issueToken_withValidUser_shouldReturnToken() {
        User user = buildUser(1L, "openid_001", 1);

        JwtService.Token token = jwtService.issueToken(user);

        assertThat(token).isNotNull();
        assertThat(token.token()).isNotBlank();
        assertThat(token.token().split("\\.")).hasSize(3);
        assertThat(token.expiresInSeconds()).isEqualTo(3600);
    }

    @Test
    @DisplayName("签发 - null 用户：抛出 NullPointerException")
    void issueToken_withNullUser_shouldThrowNPE() {
        assertThatNullPointerException()
                .isThrownBy(() -> jwtService.issueToken(null));
    }

    @Test
    @DisplayName("签发 - 用户 ID 为 null：抛出 IllegalArgumentException")
    void issueToken_withNullUserId_shouldThrow() {
        User user = buildUser(null, "openid_001", 1);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> jwtService.issueToken(user))
                .withMessageContaining("id");
    }

    // ======================== 校验 ========================

    @Test
    @DisplayName("校验 - 合法 token：能解析出正确 userId/openid/userType")
    void verify_withValidToken_shouldReturnVerifiedJwt() {
        User user = buildUser(42L, "openid_verify", 2);
        String rawToken = jwtService.issueToken(user).token();

        JwtService.VerifiedJwt verified = jwtService.verify(rawToken);

        assertThat(verified.userId()).isEqualTo(42L);
        assertThat(verified.openid()).isEqualTo("openid_verify");
        assertThat(verified.userType()).isEqualTo(2);
        assertThat(verified.expiresAt()).isNotNull();
    }

    @Test
    @DisplayName("校验 - 签名被篡改：抛出 IllegalArgumentException（Invalid JWT signature）")
    void verify_withTamperedSignature_shouldThrow() {
        User user = buildUser(1L, "openid_001", 1);
        String rawToken = jwtService.issueToken(user).token();
        String tampered = rawToken.substring(0, rawToken.lastIndexOf('.')) + ".fakesig";

        assertThatIllegalArgumentException()
                .isThrownBy(() -> jwtService.verify(tampered))
                .withMessageContaining("signature");
    }

    @Test
    @DisplayName("校验 - token 格式不是三段：抛出 IllegalArgumentException")
    void verify_withInvalidFormat_shouldThrow() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> jwtService.verify("not.a.valid.jwt.token"))
                .withMessageContaining("Invalid JWT format");
    }

    @Test
    @DisplayName("校验 - 空白 token：抛出 IllegalArgumentException")
    void verify_withBlankToken_shouldThrow() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> jwtService.verify("  "));
    }

    @Test
    @DisplayName("校验 - 已过期 token：抛出 IllegalArgumentException（JWT expired）")
    void verify_withExpiredToken_shouldThrow() {
        JwtProperties shortProps = new JwtProperties();
        shortProps.setSecret(TEST_SECRET);
        shortProps.setTtlSeconds(-1);
        JwtService shortService = new JwtService(shortProps, new ObjectMapper());

        User user = buildUser(1L, "openid_exp", 1);
        String expiredToken = shortService.issueToken(user).token();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> shortService.verify(expiredToken))
                .withMessageContaining("expired");
    }

    // ======================== 构造器参数校验 ========================

    @Test
    @DisplayName("构造器 - secret 过短（< 32 字节）：抛出 IllegalStateException")
    void constructor_withShortSecret_shouldThrow() {
        JwtProperties props = new JwtProperties();
        props.setSecret("tooshort");

        assertThatIllegalStateException()
                .isThrownBy(() -> new JwtService(props, new ObjectMapper()))
                .withMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("构造器 - secret 为空：抛出 IllegalStateException")
    void constructor_withBlankSecret_shouldThrow() {
        JwtProperties props = new JwtProperties();
        props.setSecret("");

        assertThatIllegalStateException()
                .isThrownBy(() -> new JwtService(props, new ObjectMapper()));
    }

    // ======================== 工具方法 ========================

    private User buildUser(Long id, String openid, Integer userType) {
        User user = new User();
        user.setId(id);
        user.setOpenid(openid);
        user.setUserType(userType);
        return user;
    }
}

