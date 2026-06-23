package com.huawei.theme.analysis.core.diagnostic.adapter;

import java.io.IOException;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import com.huawei.theme.analysis.core.diagnostic.DiagnosticSeverity;

/**
 * DiagnosticSeverity的GSON序列化适配器，实现枚举与JSON字符串的双向映射。
 *
 * <p>JSON中severity存储为小写字符串("error"/"warning"/"info")，
 * Java内存中为DiagnosticSeverity枚举常量(ERROR/WARNING/INFO)。
 * 此适配器用于规则库JSON反序列化时将字符串映射为枚举。</p>
 */
public class DiagnosticSeverityAdapter extends TypeAdapter<DiagnosticSeverity> {

    /**
     * 将DiagnosticSeverity枚举序列化为JSON小写字符串。
     *
     * @param out JSON写入器
     * @param severity 诊断严重级别枚举值，null时写入JSON null
     */
    @Override
    public void write(JsonWriter out, DiagnosticSeverity severity) throws IOException {
        if (severity == null) {
            out.nullValue();
            return;
        }
        out.value(severity.name().toLowerCase());
    }

    /**
     * 将JSON字符串反序列化为DiagnosticSeverity枚举。
     *
     * <p>处理逻辑：JSON null → Java null；空字符串 → Java null；
     * 有效字符串 → 转为大写后匹配枚举常量。</p>
     *
     * @param in JSON读取器
     * @return 对应的DiagnosticSeverity枚举值，JSON null或空字符串时返回null
     */
    @Override
    public DiagnosticSeverity read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        String value = in.nextString();
        if (value == null || value.isEmpty()) {
            return null;
        }
        return DiagnosticSeverity.valueOf(value.toUpperCase());
    }
}
