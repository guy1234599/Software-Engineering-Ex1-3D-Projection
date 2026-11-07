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
 * Reads either the project's original coordinate-only CSV (9 or 12 numeric
 * columns) or the lecturer's spreadsheet CSV (an optional official heading,
 * followed by 3 or 4 and its coordinates).
 * Blank lines are ignored; arbitrary comments and quoted numeric fields are
 * not supported. Invalid geometry fails with a precise source line number.
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
            boolean officialHeader = false;
            boolean sawRecord = false;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                // Some spreadsheet exports prepend a UTF-8 BOM to the first line.
                if (lineNumber == 1 && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                if (line.isBlank()) {
                    continue;
                }
                if (!sawRecord && isOfficialHeader(line)) {
                    officialHeader = true;
                    sawRecord = true;
                    continue;
                }
                sawRecord = true;
                planes.add(parseLine(file, lineNumber, line, officialHeader));
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

    /** Do not silently skip arbitrary text: recognize the exact official column layout. */
    private static boolean isOfficialHeader(String line) {
        String[] cells = line.split(",", -1);
        if (cells.length != 13 || !cells[0].trim().replace("\"", "")
                .equalsIgnoreCase("Number of Points (3 or 4)")) {
            return false;
        }
        for (int i = 1; i <= 12; i++) {
            String expected = "" + "xyz".charAt((i - 1) % 3) + ((i - 1) / 3 + 1);
            if (!cells[i].trim().equalsIgnoreCase(expected)) return false;
        }
        return true;
    }

    private static PlaneDefinition parseLine(Path file, int lineNumber, String line,
                                             boolean officialHeader) throws PlaneCsvException {
        String[] original = line.split(",", -1);
        String[] tokens;
        if (!officialHeader && (original.length == 9 || original.length == 12)) {
            tokens = original; // Existing coordinate-only format.
        } else {
            if (original.length != 10 && original.length != 13) {
                throw new PlaneCsvException(file, lineNumber,
                        "Expected 9 or 12 coordinates, or 3/4 followed by coordinates; found "
                                + original.length + " CSV columns");
            }
            String count = original[0].trim();
            if (!count.equals("3") && !count.equals("4")) {
                throw new PlaneCsvException(file, lineNumber,
                        "Expected 3 or 4 in the first 'Number of Points' column, found '"
                                + count + "'");
            }
            int coordinateCount = count.equals("3") ? 9 : 12;
            if (original.length < coordinateCount + 1) {
                throw new PlaneCsvException(file, lineNumber,
                        "Point count " + count + " requires " + coordinateCount + " coordinates");
            }
            // Google Sheets CSV exports may keep blank x4/y4/z4 fields in
            // 3-point rows under the official 13-column header.
            for (int i = coordinateCount + 1; i < original.length; i++) {
                if (!original[i].isBlank()) {
                    throw new PlaneCsvException(file, lineNumber,
                            "Unexpected coordinate " + i + " after the " + count
                                    + " declared points");
                }
            }
            tokens = java.util.Arrays.copyOfRange(original, 1, 1 + coordinateCount);
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
