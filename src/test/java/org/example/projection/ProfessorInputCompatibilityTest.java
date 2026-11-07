package org.example.projection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Regression tests for the formats supplied with the lecturer's exercise. */
class ProfessorInputCompatibilityTest {
    @TempDir Path dir;

    private static final String HEADER = "Number of Points (3 or 4),x1,y1,z1,x2,y2,z2,x3,y3,z3,x4,y4,z4\n";

    @Test void readsLecturerHeaderAndPaddedThreePointRows() throws IOException {
        Path csv = write("professor.csv", HEADER
                + "3,2,0,0,0,4,0,4,4,0,,,\n"
                + "4,1,0,0,3,0,0,0,4,0,4,4,0\n");
        List<PlaneDefinition> planes = new CsvPlaneReader().read(csv);
        assertEquals(2, planes.size());
        assertInstanceOf(TrianglePlane.class, planes.get(0));
        assertInstanceOf(TrapezoidPlane.class, planes.get(1));
    }

    @Test void readsProfessorStyleRowsWithoutHeader() throws IOException {
        Path csv = write("noheader.csv", "3,2,0,0,0,4,0,4,4,0\n"
                + "4,1,0,0,3,0,0,0,4,0,4,4,0\n");
        assertEquals(2, new CsvPlaneReader().read(csv).size());
    }

    @Test void readsOriginalNineCoordinateRowsUnchanged() throws IOException {
        Path csv = write("legacy.csv", "2,0,0,0,4,0,4,4,0\n");
        assertInstanceOf(TrianglePlane.class, new CsvPlaneReader().read(csv).get(0));
    }

    @Test void rejectsMismatchBetweenDeclaredPointCountAndValues() throws IOException {
        Path csv = write("extra.csv", HEADER + "3,2,0,0,0,4,0,4,4,0,1,1,1\n");
        CsvPlaneReader.PlaneCsvException exception = assertThrows(
                CsvPlaneReader.PlaneCsvException.class, () -> new CsvPlaneReader().read(csv));
        assertEquals(2, exception.lineNumber());
        assertTrue(exception.getMessage().contains("Unexpected coordinate"));
    }

    @Test void rejectsMalformedFirstPointCountWithLineNumber() throws IOException {
        Path csv = write("bad-count.csv", HEADER + "5,2,0,0,0,4,0,4,4,0,,,\n");
        CsvPlaneReader.PlaneCsvException exception = assertThrows(
                CsvPlaneReader.PlaneCsvException.class, () -> new CsvPlaneReader().read(csv));
        assertEquals(2, exception.lineNumber());
        assertTrue(exception.getMessage().contains("Expected 3 or 4"));
    }

    @Test void doesNotSilentlySkipUnrecognizedHeader() throws IOException {
        Path csv = write("unknown.csv", "something,not,valid\n");
        assertThrows(CsvPlaneReader.PlaneCsvException.class,
                () -> new CsvPlaneReader().read(csv));
    }

    @Test void originalProfessorSheetReportsFirstInvalidGeometryAtPhysicalLineFour() throws IOException {
        Path csv = write("original.csv", HEADER
                + "3,200,200,0,0,200,400,400,200,400,,,\n"
                + "4,200,0,0,200,0,500,200,400,0,200,400,500\n"
                + "3,200,200,-30,-50,200,400,400,250,400,,,\n"
                + "4,200,50,0,200,0,500,200,350,0,200,400,500\n");
        CsvPlaneReader.PlaneCsvException exception = assertThrows(
                CsvPlaneReader.PlaneCsvException.class, () -> new CsvPlaneReader().read(csv));
        assertEquals(4, exception.lineNumber());
        assertTrue(exception.getMessage().contains("Invalid triangle geometry"));
    }

    private Path write(String name, String content) throws IOException {
        Path path = dir.resolve(name);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }
}
