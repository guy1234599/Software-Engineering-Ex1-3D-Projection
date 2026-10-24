package org.example.projection;

import java.nio.file.Path;
import java.util.List;

/** A standalone parser demo; this does not yet create an MP4 video. */
public final class CsvPlaneReaderDemo {
    private CsvPlaneReaderDemo() { }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: CsvPlaneReaderDemo <path-to-planes.csv>");
            System.exit(2);
        }
        try {
            List<PlaneDefinition> planes = new CsvPlaneReader().read(Path.of(args[0]));
            System.out.println("Loaded " + planes.size() + " plane(s) from " + args[0]);
            for (int i = 0; i < planes.size(); i++) {
                String type = planes.get(i) instanceof TrianglePlane ? "TRIANGLE" : "TRAPEZOID";
                System.out.printf("Plane %d: %s%n", i + 1, type);
            }
        } catch (Exception ex) {
            System.err.println("Cannot load CSV: " + ex.getMessage());
            System.exit(1);
        }
    }
}
