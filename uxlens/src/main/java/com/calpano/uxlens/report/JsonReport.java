package com.calpano.uxlens.report;

import com.calpano.uxlens.model.Report;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** The report as JSON: the machine-readable form, for CI and for an agent's tool output. */
public final class JsonReport {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
            .serializeSpecialFloatingPointValues().create();

    private JsonReport() { }

    public static String render(Report report) { return GSON.toJson(report); }

    public static Path write(Report report, Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, render(report), StandardCharsets.UTF_8);
        return file;
    }
}
