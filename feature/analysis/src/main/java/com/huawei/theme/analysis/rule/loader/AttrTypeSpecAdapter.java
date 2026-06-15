package com.huawei.theme.analysis.rule.loader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import com.huawei.theme.analysis.rule.model.AttrTypeSpec;

public class AttrTypeSpecAdapter extends TypeAdapter<AttrTypeSpec> {

    @Override
    public void write(JsonWriter out, AttrTypeSpec value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        if (value.getEnumValues() != null && !value.getEnumValues().isEmpty()) {
            out.beginObject(); // 写{
            out.name("enum"); // 写属性名
            out.beginArray(); // 写[
            for (String enumVal : value.getEnumValues()) {
                out.value(enumVal); // 写枚举值
            }
            out.endArray(); // 写]
            out.endObject(); // 写}
        } else if (value.getAliases() != null && !value.getAliases().isEmpty()) {
            out.beginObject(); // 写{
            out.name("type").value(value.getType()); // 写type属性
            out.name("aliases"); // 写aliases属性
            out.beginArray(); // 写[
            for (String alias : value.getAliases()) {
                out.value(alias); // 写别名
            }
            out.endArray(); // 写]
            out.endObject(); // 写}
        } else {
            out.value(value.getType()); // 直接写类型字符串
        }
    }

    @Override
    public AttrTypeSpec read(JsonReader in) throws IOException {
        switch (in.peek()) { // 根据JSON的第一个token类型来决定如何解析
            case STRING: // 如果是字符串，直接作为type
                String typeValue = in.nextString(); // 读取字符串值
                return AttrTypeSpec.builder()
                        .type(typeValue)
                        .build(); // 构建AttrTypeSpec对象，只有type字段
            case BEGIN_OBJECT: // 如果是对象（'{'），解析type、enum和aliases字段
                in.beginObject(); // 读取对象开始符号（舍弃）
                String type = null;
                List<String> enumValues = null;
                List<String> aliases = null;
                while (in.hasNext()) { // 读取对象中的每个属性
                    String name = in.nextName(); // 读取属性名
                    if ("enum".equals(name)) { // 如果属性名是"enum"，读取枚举值列表
                        enumValues = readStringList(in); // 读取枚举值列表
                        type = "enum"; // 设置type为"enum"
                    } else if ("type".equals(name)) { // 如果属性名是"type"，读取类型字符串
                        type = in.nextString(); // 读取类型字符串
                    } else if ("aliases".equals(name)) { // 如果属性名是"aliases"，读取别名列表
                        aliases = readStringList(in); // 读取别名列表
                    } else {
                        in.skipValue(); // 如果遇到未知属性，跳过它的值
                    }
                }
                in.endObject(); // 读取对象结束符号（舍弃）
                return AttrTypeSpec.builder()
                        .type(type)
                        .enumValues(enumValues)
                        .aliases(aliases)
                        .build(); // 构建AttrTypeSpec对象，包含type、enumValues和aliases字段
            default:
                in.skipValue(); // 如果遇到未知类型，跳过它的值
                return null;
        }
    }

    private List<String> readStringList(JsonReader in) throws IOException {
        List<String> list = new ArrayList<>();
        in.beginArray();
        while (in.hasNext()) { // 读取数组中的每个元素
            list.add(in.nextString()); // 读取字符串值并添加到列表中
        }
        in.endArray();
        return list;
    }
}
