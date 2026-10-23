package com.housekeeping.platform.bootstrap.web.vo;

/**
 * 冒烟文件上传结果。
 *
 * @param bucket           桶名
 * @param objectKey        对象键
 * @param sizeBytes        文件字节数
 * @param sha256           内容摘要
 * @param url              短期访问地址
 * @param expiresInSeconds 地址有效期（秒）
 */
public record SmokeFileVo(String bucket, String objectKey, long sizeBytes, String sha256,
                          String url, int expiresInSeconds) {
}
