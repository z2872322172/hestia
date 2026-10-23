package com.housekeeping.platform.identity.application;

/**
 * 客户端信息（登录审计用）。
 *
 * <p>只保留 IP 与 UA：定位与浏览器指纹足够做安全分析，
 * 再多就是过度采集。</p>
 *
 * @param ipAddress 客户端 IP
 * @param userAgent 客户端 UA
 */
public record ClientInfo(String ipAddress, String userAgent) {

    /**
     * 空上下文（系统内部调用）。
     *
     * @return 空上下文
     */
    public static ClientInfo empty() {
        return new ClientInfo(null, null);
    }
}
