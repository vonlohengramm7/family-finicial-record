package com.familyledger.state;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 状态 API 出口兜底脱敏器（纵深防御，不替代采集器侧脱敏）：
 * 对任意用户可见文本与 data Map 做最后一层清洗，确保凭证/API Key 原文
 * 不进入 HTTP 响应、日志或快照读取路径。
 * 识别模式：OpenAI/DeepSeek sk- 前缀、GitHub ghp_/gho_、AWS AKIA、Slack xox、
 * Bearer token，以及常见密钥键名（apiKey/token/password/secret/authorization 等）的值。
 */
public final class SecretSanitizer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 形如 sk-xxx / ghp_xxx / Bearer xxx 的敏感值整体替换。 */
    private static final Pattern SENSITIVE_VALUE = Pattern.compile(
            "(?i)\\b(sk-[A-Za-z0-9_-]{6,}|ghp_[A-Za-z0-9]{20,}|gho_[A-Za-z0-9]{20,}|"
                    + "AKIA[0-9A-Z]{16}|xox[baprs]-[A-Za-z0-9-]{10,}|Bearer\\s+[A-Za-z0-9._~+/=-]{10,})\\b");

    /** JSON 中常见密钥键名的字符串值整体替换，保留键名本身便于展示结构。 */
    private static final Pattern SECRET_KEY_VALUE = Pattern.compile(
            "(?i)(\"(?:api[_-]?key|access[_-]?token|refresh[_-]?token|token|password|secret|authorization|credential)\"\\s*:\\s*\")([^\"]+)(\")");

    private SecretSanitizer() {
    }

    /** 清洗单个文本字段；null 原样返回。 */
    public static String sanitize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return SENSITIVE_VALUE.matcher(text).replaceAll("***");
    }

    /** 深度清洗 data Map：序列化→值级替换→反序列化；不可序列化时返回空 Map（不泄露）。 */
    public static Map<String, Object> sanitizeData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return data;
        }
        String json;
        try {
            json = MAPPER.writeValueAsString(data);
        } catch (Exception exception) {
            return Map.of();
        }
        json = SECRET_KEY_VALUE.matcher(json).replaceAll("$1***$3");
        json = SENSITIVE_VALUE.matcher(json).replaceAll("***");
        try {
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception exception) {
            return Map.of();
        }
    }
}
