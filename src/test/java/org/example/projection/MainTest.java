package org.example.projection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jcodec.api.awt.AWTFrameGrab;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {
    @TempDir Path dir;

    @Test void imageBoundsMatchProjectionEngine() {
        PlaneSampler sampler = new PlaneSampler(new NearestNeighborSampler());
        Volume volume = new ByteVolume(6, 6, 6, new byte[6 * 6 * 6]);
        List<PlaneDefinition> planes = List.of(
                new TrianglePlane(new Point3D(2, 0, 0), new Point3D(0, 4, 0), new Point3D(4, 4, 0)),
                new TrapezoidPlane(new Point3D(1, 1, 0), new Point3D(3, 1, 0),
                        new Point3D(0, 4, 0), new Point3D(4, 4, 0)));
        Main.CanvasSize size = Main.determineCanvas(planes);
        for (PlaneDefinition plane : planes) {
            Image2D image = sampler.project(volume, plane);
            assertTrue(size.width() >= image.width());
            assertTrue(size.height() >= image.height());
        }
        assertEquals(0, size.width() % 2);
        assertEquals(0, size.height() % 2);
        assertTrue(size.width() >= 128, "Windows-friendly minimum canvas width");
        assertTrue(size.height() >= 128, "Windows-friendly minimum canvas height");
    }

    @Test void producesMp4WithOneFramePerCsvPlane() throws Exception {
        Path nrrd = createSmallNrrd();
        Path csv = dir.resolve("planes.csv");
        Files.writeString(csv, "0.5,0,0,0,1,0,1,1,0\n0.2,0,0,0.8,0,0,0,1,0,1,1,0\n");
        Path output = dir.resolve("result.mp4");
        int code = Main.run(new String[]{nrrd.toString(), csv.toString(), output.toString()},
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()));
        assertEquals(0, code);
        assertTrue(Files.size(output) > 100, "Non-empty MP4 expected");
        // Verify MP4 frame decoding, not just the presence of file bytes.
        assertNotNull(AWTFrameGrab.getFrame(output.toFile(), 0));
        assertNotNull(AWTFrameGrab.getFrame(output.toFile(), 1));
        assertEquals(128, AWTFrameGrab.getFrame(output.toFile(), 0).getWidth());
    }

    @Test void malformedCsvDoesNotCreateOutput() throws Exception {
        Path nrrd = createSmallNrrd();
        Path csv = dir.resolve("bad.csv");
        Files.writeString(csv, "1,2,3\n");
        Path output = dir.resolve("no-video.mp4");
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        int code = Main.run(new String[]{nrrd.toString(), csv.toString(), output.toString()},
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(error));
        assertEquals(1, code);
        assertFalse(Files.exists(output));
        assertTrue(error.toString(StandardCharsets.UTF_8).contains("line 1"));
    }

    @Test void wrongNumberOfArgsPrintsUsage() {
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        int code = Main.run(new String[]{"only-one-arg"},
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(error));
        assertEquals(2, code);
        assertTrue(error.toString(StandardCharsets.UTF_8).contains("Usage:"));
    }

    private Path createSmallNrrd() throws Exception {
        Path nrrd = dir.resolve("tiny.nrrd");
        byte[] header = ("NRRD0005\n" +
                "type: unsigned char\n" +
                "dimension: 3\n" +
                "sizes: 2 2 2\n" +
                "encoding: raw\n\n").getBytes(StandardCharsets.US_ASCII);
        byte[] combined = new byte[header.length + 8];
        System.arraycopy(header, 0, combined, 0, header.length);
        for (int i = 0; i < 8; i++) combined[header.length + i] = (byte) (i * 30);
        Files.write(nrrd, combined);
        return nrrd;
    }
}
