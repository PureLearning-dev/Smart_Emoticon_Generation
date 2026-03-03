package com.purelearning.smart_meter.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * SecurityUtils 纯单元测试：
 * <p>
 * 每个用例完全控制 SecurityContextHolder，测完后自动清理。
 */
class SecurityUtilsTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("getCurrentUserOptional - 未认证：返回 empty")
    void getCurrentUserOptional_noAuth_shouldBeEmpty() {
        SecurityContextHolder.clearContext();

        assertThat(SecurityUtils.getCurrentUserOptional()).isEmpty();
    }

    @Test
    @DisplayName("getCurrentUserOptional - CurrentUser principal：返回正确用户")
    void getCurrentUserOptional_withCurrentUser_shouldReturnUser() {
        setCurrentUser(new CurrentUser(1L, "openid_001", 1));

        Optional<CurrentUser> result = SecurityUtils.getCurrentUserOptional();

        assertThat(result).isPresent();
        assertThat(result.get().userId()).isEqualTo(1L);
        assertThat(result.get().openid()).isEqualTo("openid_001");
        assertThat(result.get().userType()).isEqualTo(1);
    }

    @Test
    @DisplayName("getCurrentUserOptional - String principal（非 CurrentUser）：返回 empty")
    void getCurrentUserOptional_withStringPrincipal_shouldBeEmpty() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymous", null, List.of())
        );

        assertThat(SecurityUtils.getCurrentUserOptional()).isEmpty();
    }

    @Test
    @DisplayName("requireCurrentUser - 有效用户：返回 CurrentUser")
    void requireCurrentUser_withUser_shouldReturn() {
        setCurrentUser(new CurrentUser(99L, "openid_99", 2));

        CurrentUser cu = SecurityUtils.requireCurrentUser();

        assertThat(cu.userId()).isEqualTo(99L);
        assertThat(cu.userType()).isEqualTo(2);
    }

    @Test
    @DisplayName("requireCurrentUser - 未认证：抛出 IllegalStateException")
    void requireCurrentUser_noAuth_shouldThrow() {
        SecurityContextHolder.clearContext();

        assertThatIllegalStateException()
                .isThrownBy(SecurityUtils::requireCurrentUser)
                .withMessageContaining("No authenticated user");
    }

    @Test
    @DisplayName("requireCurrentUserId - 有效用户：返回正确 userId")
    void requireCurrentUserId_withUser_shouldReturnId() {
        setCurrentUser(new CurrentUser(42L, "openid_42", 1));

        assertThat(SecurityUtils.requireCurrentUserId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("requireCurrentUserId - 未认证：抛出 IllegalStateException")
    void requireCurrentUserId_noAuth_shouldThrow() {
        SecurityContextHolder.clearContext();

        assertThatIllegalStateException()
                .isThrownBy(SecurityUtils::requireCurrentUserId);
    }

    private void setCurrentUser(CurrentUser user) {
        String role = user.userType() != null && user.userType() == 2 ? "ROLE_ADMIN" : "ROLE_USER";
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user, null,
                        List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}

