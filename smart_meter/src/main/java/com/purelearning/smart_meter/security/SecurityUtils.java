package com.purelearning.smart_meter.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Spring Security 上下文读取工具：
 * <p>
 * - 将 Controller/Service 里获取当前用户的逻辑集中在一起
 * - 避免散落使用 {@link SecurityContextHolder} 导致空值/类型判断重复
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前登录用户（Optional）。
     *
     * @return 若已认证且 principal 为 {@link CurrentUser} 则返回；否则返回 empty
     */
    public static Optional<CurrentUser> getCurrentUserOptional() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CurrentUser currentUser) {
            return Optional.of(currentUser);
        }
        return Optional.empty();
    }

    /**
     * 获取当前登录用户（必须存在）。
     *
     * @return 当前用户
     * @throws IllegalStateException 当未登录或上下文 principal 类型不符合预期
     */
    public static CurrentUser requireCurrentUser() {
        return getCurrentUserOptional()
                .orElseThrow(() -> new IllegalStateException("No authenticated user in security context"));
    }

    /**
     * 获取当前用户 ID（必须存在）。
     *
     * @return userId
     * @throws IllegalStateException 未登录时抛出
     */
    public static Long requireCurrentUserId() {
        return requireCurrentUser().userId();
    }
}

