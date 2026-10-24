package com.housekeeping.platform.operations.domain;

import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;

import java.math.BigDecimal;

/**
 * 系统配置值解析（docs/modules/13 第 10 节）。
 *
 * <p>{@code value_json} 统一按 JSON 编码：标量类型存裸字面量（如 {@code 15}、{@code 0.3}、
 * {@code true}），字符串类型存带引号的 JSON 字符串。解析失败一律抛
 * SYSTEM_CONFIG_TYPE_INVALID，由调用方转成 400 响应；
 * JSON 类型本身的结构校验交给数据库 JSON 列兜底。</p>
 */
public final class ConfigValues {

    private ConfigValues() {
    }

    /**
     * 判断配置值类型是否受支持。
     *
     * @param valueType 类型编码
     * @return 受支持返回 {@code true}
     */
    public static boolean isSupportedType(String valueType) {
        return switch (valueType) {
            case "STRING", "INTEGER", "DECIMAL", "BOOLEAN", "JSON" -> true;
            default -> false;
        };
    }

    /**
     * 按 INTEGER 解析。
     *
     * @param valueJson 配置值
     * @return 整数值
     */
    public static long asInteger(String valueJson) {
        String text = stripQuotes(valueJson);
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException exception) {
            throw invalid("INTEGER", valueJson);
        }
    }

    /**
     * 按 DECIMAL 解析。
     *
     * @param valueJson 配置值
     * @return 十进制值
     */
    public static BigDecimal asDecimal(String valueJson) {
        try {
            return new BigDecimal(stripQuotes(valueJson));
        } catch (NumberFormatException exception) {
            throw invalid("DECIMAL", valueJson);
        }
    }

    /**
     * 按 BOOLEAN 解析。
     *
     * @param valueJson 配置值
     * @return 布尔值
     */
    public static boolean asBoolean(String valueJson) {
        String text = stripQuotes(valueJson);
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        throw invalid("BOOLEAN", valueJson);
    }

    /**
     * 按 STRING 解析（去 JSON 引号）。
     *
     * @param valueJson 配置值
     * @return 字符串值
     */
    public static String asString(String valueJson) {
        String text = valueJson == null ? "" : valueJson.trim();
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return text;
    }

    /**
     * 把用户输入的原始文本按类型编码为 {@code value_json}（修改配置时用）。
     *
     * <p>INTEGER / DECIMAL / BOOLEAN 先校验再原样存储；STRING 加 JSON 引号；
     * JSON 原样透传（结构由数据库 JSON 列兜底校验）。</p>
     *
     * @param valueType 类型编码
     * @param rawValue  用户输入的原始文本
     * @return JSON 编码后的配置值
     */
    public static String encode(String valueType, String rawValue) {
        String text = rawValue == null ? "" : rawValue.trim();
        if (text.isEmpty()) {
            throw invalid(valueType, rawValue);
        }
        return switch (valueType) {
            case "INTEGER" -> {
                try {
                    Long.parseLong(text);
                    yield text;
                } catch (NumberFormatException exception) {
                    throw invalid(valueType, rawValue);
                }
            }
            case "DECIMAL" -> {
                try {
                    new BigDecimal(text);
                    yield text;
                } catch (NumberFormatException exception) {
                    throw invalid(valueType, rawValue);
                }
            }
            case "BOOLEAN" -> {
                if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
                    yield text.toLowerCase();
                }
                throw invalid(valueType, rawValue);
            }
            case "STRING" -> "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            case "JSON" -> text;
            default -> throw invalid(valueType, rawValue);
        };
    }

    private static String stripQuotes(String valueJson) {
        String text = valueJson == null ? "" : valueJson.trim();
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private static BusinessException invalid(String valueType, String value) {
        return new BusinessException(ErrorCode.SYSTEM_CONFIG_TYPE_INVALID,
                "配置值类型不合法（" + valueType + "）");
    }
}
