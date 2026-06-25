package com.huawei.theme.analysis.core.shared.diagnostic.adapter;

import java.io.IOException;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

public class DiagnosticSeverityAdapter extends TypeAdapter<DiagnosticSeverity> {

    @Override
    public void write(JsonWriter out, DiagnosticSeverity severity) throws IOException {
        if (severity == null) {
            out.nullValue();
            return;
        }
        out.value(severity.name().toLowerCase());
    }

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
