package com.wenxinblog.analytics.common;

/**
 * 服务内细粒度 RBAC 权限校验工具。
 * 读取网关注入的 X-User-Permissions（逗号分隔）判断是否持有指定权限。
 */
public final class Permissions {
    private Permissions() {}
    public static boolean has(String header, String permission) {
        if (header == null || header.isBlank()) return false;
        for (String p : header.split(",")) if (permission.equals(p.trim())) return true;
        return false;
    }
}
