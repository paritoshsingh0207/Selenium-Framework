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
import java.nio.file.Paths;
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
            // Create the parent folder before PDFBox tries to write the report file.
            Files.createDirectories(output.toAbsolutePath().getParent());
            List<ExecutionRecord> finals = finalResults(attempts);

            try (PDDocument document = new PDDocument()) {
                /*
                 * Writer owns the current PDPageContentStream. It must close
                 * before PDDocument.save(...) is called.
                 */
                try (Writer writer = new Writer(document)) {
                    writeHeader(writer);
                    writeSummary(writer, attempts, finals, healingEvents);
                    writeDetailedResults(writer, finals);
                    writeHealingEvents(writer, healingEvents);
                    writeAttemptHistory(writer, attempts);
                }

                // All page content streams are closed at this point.
                document.save(output.toFile());
            }

            // Do not report success unless a real, non-empty file was written.
            if (!Files.exists(output) || Files.size(output) == 0L) {
                throw new IllegalStateException(
                        "PDF report was not written correctly: " + output.toAbsolutePath());
            }

            return output;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate PDF report", exception);
        }
    }

    private static void writeHeader(Writer writer) throws Exception {
        writer.title("Hybrid UI Automation - Detailed Execution Report");
        writer.line("Generated: " + Instant.now());
        writer.line("Automation engine: " + FrameworkConfig.engine());
        writer.line("Browser: " + FrameworkConfig.browser());
        writer.line("Data source: " + FrameworkConfig.dataSource());
        writer.line("Base URL: " + FrameworkConfig.baseUrl());
        writer.line("Parallel mode: " + FrameworkConfig.parallelMode()
                + " | threads: " + FrameworkConfig.threadCount());
        writer.line("Self-healing enabled: " + FrameworkConfig.selfHealingEnabled());
        writer.line("Sensitive report data visible: " + FrameworkConfig.showSensitiveReportData());
        writer.line("Operating system: " + System.getProperty("os.name")
                + " " + System.getProperty("os.version"));
        writer.line("Java version: " + System.getProperty("java.version"));
    }

    private static void writeSummary(Writer writer,
                                     List<ExecutionRecord> attempts,
                                     List<ExecutionRecord> finals,
                                     List<HealingEvent> healingEvents) throws Exception {
        writer.heading("1. Executive Summary");
        writer.line("Final tests: " + finals.size());
        writer.line("Passed: " + countTests(finals, ExecutionStatus.PASSED));
        writer.line("Failed: " + countTests(finals, ExecutionStatus.FAILED));
        writer.line("Skipped: " + countTests(finals, ExecutionStatus.SKIPPED));
        writer.line("Additional retry attempts: " + Math.max(0, attempts.size() - finals.size()));
        writer.line("Self-healing events: " + healingEvents.size());

        long actions = countEntries(finals, "ACTION", null);
        long passedActions = countEntries(finals, "ACTION", ReportEntryStatus.PASSED);
        long failedActions = countEntries(finals, "ACTION", ReportEntryStatus.FAILED);
        long assertions = countEntries(finals, "ASSERTION", null);
        long passedAssertions = countEntries(finals, "ASSERTION", ReportEntryStatus.PASSED);
        long failedAssertions = countEntries(finals, "ASSERTION", ReportEntryStatus.FAILED);

        writer.line("Recorded business actions: " + actions
                + " | passed: " + passedActions + " | failed: " + failedActions);
        writer.line("Recorded assertions: " + assertions
                + " | passed: " + passedAssertions + " | failed: " + failedAssertions);
    }

    private static void writeDetailedResults(Writer writer,
                                             List<ExecutionRecord> finals) throws Exception {
        writer.heading("2. Detailed Test Execution");

        if (finals.isEmpty()) {
            writer.line("No final test results were recorded.");
            return;
        }

        int testNumber = 1;
        for (ExecutionRecord record : finals) {
            writer.subHeading("Test " + testNumber + ": " + record.testName());
            writer.wrapped("Overall status: " + record.status());
            writer.wrapped("Data identity: " + blank(record.dataIdentity()));
            writer.wrapped("Engine / browser: " + record.engine() + " / " + record.browser());
            writer.wrapped("Attempt: " + record.attempt()
                    + " | thread: " + record.threadName());
            writer.wrapped("Started: " + Instant.ofEpochMilli(record.startTime()));
            writer.wrapped("Finished: " + Instant.ofEpochMilli(record.endTime()));
            writer.wrapped("Duration: " + record.durationMs() + " ms");

            long actionCount = countEntries(record, "ACTION", null);
            long assertionCount = countEntries(record, "ASSERTION", null);
            long passedAssertionCount = countEntries(record, "ASSERTION", ReportEntryStatus.PASSED);
            long failedAssertionCount = countEntries(record, "ASSERTION", ReportEntryStatus.FAILED);

            writer.wrapped("Evidence captured: " + actionCount + " business action(s), "
                    + assertionCount + " assertion(s) [passed=" + passedAssertionCount
                    + ", failed=" + failedAssertionCount + "]");

            writer.smallHeading("Step-by-step evidence");
            if (record.reportEntries().isEmpty()) {
                writer.wrapped("No detailed step evidence was recorded for this execution.");
            } else {
                for (ReportEntry entry : record.reportEntries()) {
                    writer.wrapped("Step " + entry.sequence()
                            + " | " + entry.type()
                            + " | " + entry.status()
                            + " | " + entry.description()
                            + (entry.durationMs() > 0 ? " | " + entry.durationMs() + " ms" : ""));

                    if (!entry.expected().trim().isEmpty()) {
                        writer.indented("Expected: " + entry.expected());
                    }
                    if (!entry.actual().trim().isEmpty()) {
                        writer.indented("Actual: " + entry.actual());
                    }
                    if (!entry.details().trim().isEmpty()) {
                        writer.indented("Details: " + entry.details());
                    }
                    writer.indented("Recorded at: " + Instant.ofEpochMilli(entry.timestamp()));
                }
            }

            if (!record.failureMessage().trim().isEmpty()) {
                writer.smallHeading("Failure details");
                writer.wrapped("Failure message: " + record.failureMessage());
            }

            if (!record.screenshotPath().trim().isEmpty()) {
                writer.smallHeading("Failure screenshot");
                writer.wrapped("Screenshot path: " + record.screenshotPath());
                writer.image(Paths.get(record.screenshotPath()), 480, 250);
            }

            writer.separator();
            testNumber++;
        }
    }

    private static void writeHealingEvents(Writer writer,
                                           List<HealingEvent> healingEvents) throws Exception {
        writer.heading("3. Self-Healing Evidence");
        if (healingEvents.isEmpty()) {
            writer.line("No self-healing was required during this execution.");
            return;
        }

        int number = 1;
        for (HealingEvent event : healingEvents) {
            writer.subHeading("Healing event " + number + ": " + event.locatorName());
            writer.wrapped("Action: " + event.action());
            writer.wrapped("Original locator: " + event.originalLocator());
            writer.wrapped("Healed locator: " + event.healedLocator());
            writer.wrapped("Page URL: " + event.pageUrl());
            if (!event.screenshotPath().trim().isEmpty()) {
                writer.wrapped("Screenshot: " + event.screenshotPath());
                writer.image(Paths.get(event.screenshotPath()), 480, 250);
            }
            number++;
        }
    }

    private static void writeAttemptHistory(Writer writer,
                                            List<ExecutionRecord> attempts) throws Exception {
        writer.heading("4. Retry and Attempt History");
        if (attempts.isEmpty()) {
            writer.line("No execution attempts were recorded.");
            return;
        }

        for (ExecutionRecord attempt : attempts) {
            writer.wrapped(attempt.status()
                    + " | " + attempt.testName()
                    + " | data=" + blank(attempt.dataIdentity())
                    + " | attempt=" + attempt.attempt()
                    + " | duration=" + attempt.durationMs() + " ms"
                    + " | " + attempt.engine() + "/" + attempt.browser()
                    + " | thread=" + attempt.threadName());
            if (!attempt.failureMessage().trim().isEmpty()) {
                writer.indented("Failure: " + attempt.failureMessage());
            }
        }
    }

    private static long countTests(List<ExecutionRecord> records, ExecutionStatus status) {
        long count = 0L;
        for (ExecutionRecord record : records) {
            if (record.status() == status) count++;
        }
        return count;
    }

    private static long countEntries(List<ExecutionRecord> records,
                                     String type,
                                     ReportEntryStatus status) {
        long count = 0L;
        for (ExecutionRecord record : records) {
            count += countEntries(record, type, status);
        }
        return count;
    }

    private static long countEntries(ExecutionRecord record,
                                     String type,
                                     ReportEntryStatus status) {
        long count = 0L;
        for (ReportEntry entry : record.reportEntries()) {
            boolean typeMatches = type == null || type.equalsIgnoreCase(entry.type());
            boolean statusMatches = status == null || status == entry.status();
            if (typeMatches && statusMatches) count++;
        }
        return count;
    }

    private static List<ExecutionRecord> finalResults(List<ExecutionRecord> attempts) {
        Map<String, ExecutionRecord> map = new LinkedHashMap<>();
        attempts.stream()
                .sorted(Comparator.comparingInt(ExecutionRecord::attempt)
                        .thenComparingLong(ExecutionRecord::endTime))
                .forEach(record -> map.put(record.executionKey(), record));
        return new ArrayList<>(map.values());
    }

    private static String blank(String value) {
        return value == null || value.trim().isEmpty() ? "<not available>" : value;
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

        private void title(String value) throws Exception {
            write(value, bold, 18, 27, MARGIN);
        }

        private void heading(String value) throws Exception {
            ensure(38);
            y -= 8;
            write(value, bold, 13, 21, MARGIN);
        }

        private void subHeading(String value) throws Exception {
            ensure(30);
            write(value, bold, 11, 18, MARGIN);
        }

        private void smallHeading(String value) throws Exception {
            ensure(26);
            write(value, bold, 10, 16, MARGIN + 8);
        }

        private void line(String value) throws Exception {
            write(value, normal, 9, 14, MARGIN);
        }

        private void wrapped(String value) throws Exception {
            wrappedAt(value, MARGIN, 95);
        }

        private void indented(String value) throws Exception {
            wrappedAt(value, MARGIN + 18, 90);
        }

        private void wrappedAt(String value, float x, int maxCharacters) throws Exception {
            for (String line : wrap(sanitize(value), maxCharacters)) {
                write(line, normal, 9, 14, x);
            }
        }

        private void separator() throws Exception {
            ensure(16);
            y -= 5;
            stream.moveTo(MARGIN, y);
            stream.lineTo(page.getMediaBox().getWidth() - MARGIN, y);
            stream.stroke();
            y -= 11;
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

        private void write(String value, PDType1Font font, float size,
                           float spacing, float x) throws Exception {
            ensure(spacing);
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, y);
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
                if (current.length() + word.length() + 1 > maxCharacters && current.length() > 0) {
                    lines.add(current.toString());
                    current = new StringBuilder();
                }
                if (current.length() > 0) current.append(' ');
                current.append(word);
            }
            if (current.length() > 0) lines.add(current.toString());
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
