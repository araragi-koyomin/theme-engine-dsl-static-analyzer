package com.huawei.theme.analysis.core.shared.type.adapter;

import java.io.IOException;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import com.huawei.theme.analysis.core.shared.type.DslArrayType;
import com.huawei.theme.analysis.core.shared.type.DslNumberType;
import com.huawei.theme.analysis.core.shared.type.DslStringType;
import com.huawei.theme.analysis.core.shared.type.DslType;

public class DslTypeAdapter extends TypeAdapter<DslType> {

    @Override
    public void write(JsonWriter out, DslType type) throws IOException {
        if (type == null) {
            out.nullValue();
            return;
        }
        if (type instanceof DslArrayType) {
            out.value(((DslArrayType) type).getBaseType() + "[]");
        } else {
            out.value(type.getName());
        }
    }

    @Override
    public DslType read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        String value = in.nextString();
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (value.endsWith("[]")) {
            String baseType = value.substring(0, value.length() - 2);
            return DslArrayType.builder().baseType(baseType).build();
        }
        if ("number".equals(value)) {
            return new DslNumberType();
        }
        if ("string".equals(value)) {
            return new DslStringType();
        }
        throw new IOException("Unknown DslType: " + value);
    }
}
