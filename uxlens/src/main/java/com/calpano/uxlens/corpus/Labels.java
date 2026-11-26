package com.calpano.uxlens.corpus;

import com.calpano.uxlens.model.Finding;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A human's verdict on individual findings.
 *
 * <p>This is the part that cannot be automated, and the part that makes everything else
 * worth automating: somebody looks at the page, looks at the finding, and says whether the
 * tool was right. Precision then stops being a matter of opinion.
 */
public final class Labels {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** What a reviewer concluded about one finding. */
    public enum Verdict {
        /** The tool was right; this is a defect worth a developer's time. */
        TRUE_POSITIVE,
        /** The tool was wrong, or right but about nothing anyone should act on. */
        FALSE_POSITIVE,
        /** Genuinely debatable. Counted separately rather than quietly rounded either way. */
        UNSURE
    }

    public record Label(Verdict verdict, String note) { }

    private final Map<String, Label> byKey;

    private Labels(Map<String, Label> byKey) { this.byKey = byKey; }

    public static Labels empty() { return new Labels(new LinkedHashMap<>()); }

    public static Labels load(Path dir) {
        Path file = dir.resolve("labels.json");
        if (!Files.exists(file)) return empty();
        try {
            Map<String, Label> map = GSON.fromJson(
                    Files.readString(file, StandardCharsets.UTF_8),
                    new TypeToken<LinkedHashMap<String, Label>>() { }.getType());
            return new Labels(map == null ? new LinkedHashMap<>() : map);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void save(Path dir) {
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("labels.json"), GSON.toJson(byKey), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A stable identity for a finding across runs: which capture, which rule, which element.
     *
     * <p>Deliberately excludes the message, so that rewording a finding does not throw away
     * the verdict somebody spent time forming.
     */
    public static String key(CorpusStore.Entry entry, Finding finding) {
        String where = finding.selectors.isEmpty() ? "page" : finding.selectors.get(0);
        return entry.key() + "|" + finding.ruleId + "|" + where;
    }

    public Optional<Label> get(String key) { return Optional.ofNullable(byKey.get(key)); }

    public void put(String key, Verdict verdict, String note) {
        byKey.put(key, new Label(verdict, note));
    }

    public int size() { return byKey.size(); }

    public Map<String, Label> all() { return java.util.Collections.unmodifiableMap(byKey); }
}
