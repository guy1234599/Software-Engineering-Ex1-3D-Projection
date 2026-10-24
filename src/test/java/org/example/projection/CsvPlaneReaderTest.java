package org.example.projection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvPlaneReaderTest {
    @TempDir Path tempDir;
    private final CsvPlaneReader parser = new CsvPlaneReader();

    @Test void readsTriangleAndTrapezoidInOriginalOrder() throws IOException {
        Path file = write("planes.csv", "2,0,0,0,4,0,4,4,0\n" +
                "1,0,0,3,0,0,0,4,0,4,4,0\n" +
                "2,0,1,0,4,1,4,4,1\n");
        List<PlaneDefinition> planes = parser.read(file);
        assertEquals(3, planes.size());
        assertInstanceOf(TrianglePlane.class, planes.get(0));
        assertInstanceOf(TrapezoidPlane.class, planes.get(1));
        assertInstanceOf(TrianglePlane.class, planes.get(2));
        assertEquals(new Point3D(2, 0, 1), ((TrianglePlane) planes.get(2)).apex());
        assertThrows(UnsupportedOperationException.class, () -> planes.clear());
    }

    @Test void readsDecimalCoordinatesAndScientificNotation() throws IOException {
        Path file = write("decimal.csv", "0.5, 0, 0, 0, 1, 0, 1, 1e0, 0\n");
        TrianglePlane triangle = (TrianglePlane) parser.read(file).get(0);
        assertEquals(0.5, triangle.apex().x());
        assertEquals(1.0, triangle.baseRight().y());
    }

    @Test void ignoresBlankLinesButReportsPhysicalLineNumber() throws IOException {
        Path file = write("blank.csv", " \n\n2,0,0,0,4,0,4,4,0\n\n2,0,0,0,4,0,4,4,0\n");
        assertEquals(2, parser.read(file).size());
    }

    @Test void toleratesUtf8BomOnFirstLine() throws IOException {
        Path file = write("bom.csv", "\uFEFF2,0,0,0,4,0,4,4,0\n");
        assertEquals(1, parser.read(file).size());
    }

    @Test void rejectsWrongCoordinateCountWithLineNumber() throws IOException {
        Path file = write("bad-count.csv", "2,0,0,0,4,0,4,4,0\n\n1,2,3\n");
        CsvPlaneReader.PlaneCsvException error = assertThrows(
                CsvPlaneReader.PlaneCsvException.class, () -> parser.read(file));
        assertEquals(3, error.lineNumber());
        assertEquals(file, error.file());
        assertTrue(error.getMessage().contains("Expected 9"));
        assertTrue(error.getMessage().contains("line 3"));
        assertTrue(error.getMessage().contains("bad-count.csv"));
    }

    @Test void rejectsMalformedNumberAndPointsToCoordinate() throws IOException {
        Path file = write("bad-number.csv", "2,0,0,0,4,0,4,what,0\n");
        CsvPlaneReader.PlaneCsvException error = assertThrows(
                CsvPlaneReader.PlaneCsvException.class, () -> parser.read(file));
        assertEquals(1, error.lineNumber());
        assertTrue(error.getMessage().contains("Coordinate 8"));
    }

    @Test void rejectsNanAndInfinity() throws IOException {
        for (String value : List.of("NaN", "Infinity", "1e999", "0x1.0p1")) {
            Path file = write("nonfinite.csv", value + ",0,0,0,4,0,4,4,0\n");
            assertThrows(CsvPlaneReader.PlaneCsvException.class, () -> parser.read(file), value);
        }
    }

    @Test void rejectsInvalidTriangleGeometryWithExplanation() throws IOException {
        Path file = write("bad-triangle.csv", "0,0,0,2,0,0,0,3,0\n");
        CsvPlaneReader.PlaneCsvException error = assertThrows(
                CsvPlaneReader.PlaneCsvException.class, () -> parser.read(file));
        assertTrue(error.getMessage().contains("Invalid triangle geometry"));
        assertEquals(1, error.lineNumber());
        assertTrue(error.getMessage().contains("Processing stopped"));
    }

    @Test void rejectsInvalidTrapezoidGeometry() throws IOException {
        Path file = write("bad-trapezoid.csv", "1,0,0,3,0,0,0,4,0,5,4,0\n");
        CsvPlaneReader.PlaneCsvException error = assertThrows(
                CsvPlaneReader.PlaneCsvException.class, () -> parser.read(file));
        assertTrue(error.getMessage().contains("Invalid trapezoid geometry"));
    }

    @Test void rejectsTrailingComma() throws IOException {
        Path file = write("comma.csv", "2,0,0,0,4,0,4,4,0,\n");
        assertThrows(CsvPlaneReader.PlaneCsvException.class, () -> parser.read(file));
    }

    @Test void rejectsEmptyFile() throws IOException {
        Path file = write("empty.csv", "\n  \n");
        CsvPlaneReader.PlaneCsvException error = assertThrows(
                CsvPlaneReader.PlaneCsvException.class, () -> parser.read(file));
        assertEquals(0, error.lineNumber());
        assertTrue(error.getMessage().contains("No plane definitions"));
    }

    @Test void rejectsHeader() throws IOException {
        Path file = write("header.csv", "Ax,Ay,Az,Bx,By,Bz,Cx,Cy,Cz\n");
        assertThrows(CsvPlaneReader.PlaneCsvException.class, () -> parser.read(file));
    }

    @Test void missingFileIsClearIoException() {
        Path missing = tempDir.resolve("missing.csv");
        IOException error = assertThrows(IOException.class, () -> parser.read(missing));
        assertTrue(error.getMessage().contains("missing.csv"));
    }

    private Path write(String fileName, String text) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }
}
