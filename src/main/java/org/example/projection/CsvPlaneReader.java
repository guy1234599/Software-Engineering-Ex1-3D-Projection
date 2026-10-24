package org.example.projection;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reads the assignment's numeric CSV format: one plane per line, without a header.
 * Nine values define triangle (A,B,C); twelve define trapezoid (A1,A2,B,C).
 * Blank lines are ignored; CSV comments and quoted fields are not supported.
 * All records are validated before an immutable, ordered result is returned.
 */
public final class CsvPlaneReader {
    private static final java.util.regex.Pattern DECIMAL = java.util.regex.Pattern.compile(
            "[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");

    public List<PlaneDefinition> read(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        List<PlaneDefinition> planes = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                // Some spreadsheet exports prepend a UTF-8 BOM to the first line.
                if (lineNumber == 1 && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                if (line.isBlank()) {
                    continue;
                }
                planes.add(parseLine(file, lineNumber, line));
            }
            if (planes.isEmpty()) {
                throw new PlaneCsvException(file, 0, "No plane definitions found");
            }
        } catch (PlaneCsvException e) {
            throw e;
        } catch (IOException e) {
            throw new IOException("Cannot read plane CSV '" + file + "': " + e.getMessage(), e);
        }
        return List.copyOf(planes);
    }

    private static PlaneDefinition parseLine(Path file, int lineNumber, String line)
            throws PlaneCsvException {
        String[] tokens = line.split(",", -1);
        if (tokens.length != 9 && tokens.length != 12) {
            throw new PlaneCsvException(file, lineNumber,
                    "Expected 9 coordinates (triangle) or 12 (trapezoid), found "
                            + tokens.length);
        }
        double[] values = new double[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i].trim();
            if (token.isEmpty()) {
                throw new PlaneCsvException(file, lineNumber,
                        "Coordinate " + (i + 1) + " is empty");
            }
            if (!DECIMAL.matcher(token).matches()) {
                throw new PlaneCsvException(file, lineNumber,
                        "Coordinate " + (i + 1) + " is not a decimal number: '" + token + "'");
            }
            try {
                values[i] = Double.parseDouble(token);
            } catch (NumberFormatException ex) {
                throw new PlaneCsvException(file, lineNumber,
                        "Coordinate " + (i + 1) + " is not a number: '" + token + "'", ex);
            }
            if (!Double.isFinite(values[i])) {
                throw new PlaneCsvException(file, lineNumber,
                        "Coordinate " + (i + 1) + " must be finite, found '" + token + "'");
            }
        }
        try {
            Point3D p0 = point(values, 0);
            Point3D p1 = point(values, 3);
            Point3D p2 = point(values, 6);
            if (values.length == 9) {
                return new TrianglePlane(p0, p1, p2);
            }
            Point3D p3 = point(values, 9);
            return new TrapezoidPlane(p0, p1, p2, p3);
        } catch (IllegalArgumentException ex) {
            throw new PlaneCsvException(file, lineNumber,
                    "Invalid " + (values.length == 9 ? "triangle" : "trapezoid")
                            + " geometry: " + ex.getMessage(), ex);
        }
    }

    private static Point3D point(double[] values, int from) {
        return new Point3D(values[from], values[from + 1], values[from + 2]);
    }

    /** Recoverable input error with the exact physical CSV line number (0 for an empty file). */
    public static final class PlaneCsvException extends IOException {
        private final Path file;
        private final int lineNumber;

        public PlaneCsvException(Path file, int lineNumber, String reason) {
            super(message(file, lineNumber, reason));
            this.file = file;
            this.lineNumber = lineNumber;
        }

        public PlaneCsvException(Path file, int lineNumber, String reason, Throwable cause) {
            super(message(file, lineNumber, reason), cause);
            this.file = file;
            this.lineNumber = lineNumber;
        }

        private static String message(Path file, int line, String reason) {
            return "CSV error in '" + file + "'" + (line > 0 ? " at line " + line : "")
                    + ": " + reason + ". Processing stopped; correct the CSV and try again.";
        }

        public Path file() { return file; }
        public int lineNumber() { return lineNumber; }
    }
}
