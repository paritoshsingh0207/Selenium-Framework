package com.hybrid.framework.reporting;

import com.hybrid.framework.config.FrameworkConfig;
import com.hybrid.framework.healing.HealingEvent;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PdfReportGenerator {
    private PdfReportGenerator() {
    }

    public static Path generate(List<ExecutionRecord> attempts, List<HealingEvent> healingEvents) {
        Path output = FrameworkConfig.pdfReportPath();
        try {
            Files.createDirectories(output.toAbsolutePath().getParent());
            List<ExecutionRecord> finals = finalResults(attempts);
            try (PDDocument document = new PDDocument(); Writer writer = new Writer(document)) {
                writer.title("Hybrid UI Automation Report");
                writer.line("Generated: " + Instant.now());
                writer.line("Engine/browser: " + FrameworkConfig.engine() + "/" + FrameworkConfig.browser());
                writer.line("Data source: " + FrameworkConfig.dataSource());
                writer.line("Parallel: " + FrameworkConfig.parallelMode() + " / threads " + FrameworkConfig.threadCount());
                writer.line("Self-healing: " + FrameworkConfig.selfHealingEnabled());

                writer.heading("Summary");
                writer.line("Final tests: " + finals.size());
                writer.line("Passed: " + finals.stream().filter(r -> r.status() == ExecutionStatus.PASSED).count());
                writer.line("Failed: " + finals.stream().filter(r -> r.status() == ExecutionStatus.FAILED).count());
                writer.line("Skipped: " + finals.stream().filter(r -> r.status() == ExecutionStatus.SKIPPED).count());
                writer.line("Additional attempts: " + Math.max(0, attempts.size() - finals.size()));
                writer.line("Healing events: " + healingEvents.size());

                writer.heading("Final results");
                for (ExecutionRecord record : finals) {
                    writer.wrapped(record.status() + " | " + record.testName() + " | " + record.dataIdentity()
                            + " | attempt " + record.attempt() + " | " + record.durationMs() + " ms");
                }

                List<ExecutionRecord> failures = finals.stream()
                        .filter(record -> record.status() == ExecutionStatus.FAILED).toList();
                if (!failures.isEmpty()) {
                    writer.heading("Failures");
                    for (ExecutionRecord failure : failures) {
                        writer.subHeading(failure.testName());
                        writer.wrapped("Data: " + failure.dataIdentity());
                        writer.wrapped("Message: " + blank(failure.failureMessage()));
                        writer.wrapped("Screenshot: " + blank(failure.screenshotPath()));
                        if (!failure.screenshotPath().isBlank()) {
                            writer.image(Path.of(failure.screenshotPath()), 480, 250);
                        }
                    }
                }

                writer.heading("Self-healing events");
                if (healingEvents.isEmpty()) {
                    writer.line("No healing was required.");
                } else {
                    for (HealingEvent event : healingEvents) {
                        writer.subHeading(event.locatorName());
                        writer.wrapped("Action: " + event.action());
                        writer.wrapped("Original: " + event.originalLocator());
                        writer.wrapped("Healed: " + event.healedLocator());
                        writer.wrapped("URL: " + event.pageUrl());
                        if (!event.screenshotPath().isBlank()) {
                            writer.image(Path.of(event.screenshotPath()), 480, 250);
                        }
                    }
                }

                writer.heading("All attempts");
                for (ExecutionRecord attempt : attempts) {
                    writer.wrapped(attempt.status() + " | " + attempt.testName() + " | attempt " + attempt.attempt()
                            + " | " + attempt.engine() + "/" + attempt.browser() + " | " + attempt.threadName());
                }
            }
            return output;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate PDF report", exception);
        }
    }

    private static List<ExecutionRecord> finalResults(List<ExecutionRecord> attempts) {
        Map<String, ExecutionRecord> map = new LinkedHashMap<>();
        attempts.stream().sorted(Comparator.comparingInt(ExecutionRecord::attempt)
                        .thenComparingLong(ExecutionRecord::endTime))
                .forEach(record -> map.put(record.executionKey(), record));
        return new ArrayList<>(map.values());
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "<not available>" : value;
    }

    private static final class Writer implements AutoCloseable {
        private static final float MARGIN = 45;
        private static final float BOTTOM = 45;
        private final PDDocument document;
        private final PDType1Font normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPage page;
        private PDPageContentStream stream;
        private float y;

        private Writer(PDDocument document) throws Exception {
            this.document = document;
            newPage();
        }

        private void title(String value) throws Exception { write(value, bold, 18, 26); }
        private void heading(String value) throws Exception { ensure(35); y -= 8; write(value, bold, 13, 20); }
        private void subHeading(String value) throws Exception { ensure(28); write(value, bold, 11, 18); }
        private void line(String value) throws Exception { write(value, normal, 9, 14); }

        private void wrapped(String value) throws Exception {
            for (String line : wrap(sanitize(value), 95)) {
                write(line, normal, 9, 14);
            }
        }

        private void image(Path path, float maxWidth, float maxHeight) throws Exception {
            if (!Files.exists(path)) return;
            PDImageXObject image = PDImageXObject.createFromFileByContent(path.toFile(), document);
            float scale = Math.min(maxWidth / image.getWidth(), maxHeight / image.getHeight());
            float width = image.getWidth() * scale;
            float height = image.getHeight() * scale;
            ensure(height + 18);
            stream.drawImage(image, MARGIN, y - height, width, height);
            y -= height + 12;
        }

        private void write(String value, PDType1Font font, float size, float spacing) throws Exception {
            ensure(spacing);
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(MARGIN, y);
            stream.showText(sanitize(value));
            stream.endText();
            y -= spacing;
        }

        private void ensure(float space) throws Exception {
            if (y - space < BOTTOM) newPage();
        }

        private void newPage() throws Exception {
            if (stream != null) stream.close();
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        private List<String> wrap(String value, int maxCharacters) {
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String word : value.split("\\s+")) {
                if (current.length() + word.length() + 1 > maxCharacters && !current.isEmpty()) {
                    lines.add(current.toString());
                    current = new StringBuilder();
                }
                if (!current.isEmpty()) current.append(' ');
                current.append(word);
            }
            if (!current.isEmpty()) lines.add(current.toString());
            if (lines.isEmpty()) lines.add("");
            return lines;
        }

        private String sanitize(String value) {
            return value == null ? "" : value.replaceAll("[^\\x20-\\x7E]", "?");
        }

        @Override
        public void close() throws Exception {
            if (stream != null) stream.close();
        }
    }
}
