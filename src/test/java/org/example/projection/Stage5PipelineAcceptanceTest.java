package org.example.projection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jcodec.api.awt.AWTFrameGrab;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end checks against real JCodec files, not mocked video encoders. */
class Stage5PipelineAcceptanceTest {
    @TempDir Path dir;

    @Test void mixedPlaneCsvProducesThreeDecodableFramesInCsvOrder() throws Exception {
        Path volume = createLayerVolume(false);
        Path planes = dir.resolve("ordered-planes.csv");
        // Deliberately out of z order: bright -> black -> medium.
        // The middle plane is a trapezoid, so this also exercises mixed CSV shapes.
        Files.writeString(planes, triangle(2) + trapezoid(0) + triangle(1), StandardCharsets.UTF_8);
        Path output = dir.resolve("ordered.mp4");

        ByteArrayOutputStream messages = new ByteArrayOutputStream();
        int status = Main.run(new String[]{volume.toString(), planes.toString(), output.toString()},
                new PrintStream(messages), new PrintStream(messages));
        assertEquals(0, status, messages.toString(StandardCharsets.UTF_8));
        assertTrue(Files.size(output) > 500, "Produced MP4 should not be a zero-byte placeholder");

        BufferedImage first = AWTFrameGrab.getFrame(output.toFile(), 0);
        BufferedImage second = AWTFrameGrab.getFrame(output.toFile(), 1);
        BufferedImage third = AWTFrameGrab.getFrame(output.toFile(), 2);
        assertNotNull(first);
        assertNotNull(second);
        assertNotNull(third);
        for (BufferedImage image : new BufferedImage[]{first,second,third}) {
            assertEquals(128, image.getWidth());
            assertEquals(128, image.getHeight());
        }
        double bright = meanGray(first);
        double black = meanGray(second);
        double middle = meanGray(third);
        // Lossy H.264 prevents exact per-pixel assertions; wide margins make
        // a reversed/duplicated frame order observable while tolerating codec noise.
        assertTrue(bright > middle + 2.0,
                "Expected z=2 to be brighter than z=1; means " + bright + " / " + middle);
        assertTrue(middle > black + 2.0,
                "Expected z=1 to be brighter than z=0; means " + middle + " / " + black);
    }

    @Test void gzipNrrdGivesExactlyTheSameVoxelValuesAsRawNrrd() throws Exception {
        Path raw = createLayerVolume(false);
        Path gz = createLayerVolume(true);
        Volume a = new NrrdReader().read(raw);
        Volume b = new NrrdReader().read(gz);
        for (int z=0;z<3;z++) for (int y=0;y<40;y++) for (int x=0;x<40;x++) {
            assertEquals(a.getVoxel(x,y,z), b.getVoxel(x,y,z),
                    "gzip mismatch at voxel " + x + "," + y + "," + z);
        }
    }

    @Test void invalidCsvPreservesExistingMp4AndReportsThePhysicalLine() throws Exception {
        Path planes = dir.resolve("bad-late.csv");
        Files.writeString(planes, triangle(2) + "\n" + "this,is,not,a,valid,plane\n");
        Path output = dir.resolve("already-exists.mp4");
        byte[] previousOutput = {1,3,3,7};
        Files.write(output, previousOutput);
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int status = Main.run(new String[]{createLayerVolume(false).toString(),
                        planes.toString(), output.toString()},
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(errors));
        assertEquals(1, status);
        assertArrayEquals(previousOutput, Files.readAllBytes(output),
                "Bad CSV must not destroy previously published MP4");
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("line 3"),
                "Expected physical CSV line number, got: " + errors);
    }

    @Test void invalidNrrdDoesNotPublishPartialOutputOrLeaveStagingFiles() throws Exception {
        Path badVolume = dir.resolve("truncated.nrrd");
        byte[] header = header("raw");
        Files.write(badVolume, concat(header, new byte[]{1,2,3}));
        Path csv = dir.resolve("valid.csv");
        Files.writeString(csv, triangle(0));
        Path output = dir.resolve("should-not-exist.mp4");
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        int status = Main.run(new String[]{badVolume.toString(),csv.toString(),output.toString()},
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(error));
        assertEquals(1,status);
        assertFalse(Files.exists(output));
        try (var paths = Files.list(dir)) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString()
                    .startsWith(".volume-projection-")), "No orphaned staging files expected");
        }
    }

    private Path createLayerVolume(boolean gzip) throws Exception {
        // 40x40x3; constant intensity in each layer: 0, 125, 250.
        byte[] pixels = new byte[40*40*3];
        Arrays.fill(pixels, 40*40, 2*40*40, (byte)125);
        Arrays.fill(pixels, 2*40*40, 3*40*40, (byte)250);
        Path file = dir.resolve(gzip ? "layers-gzip.nrrd" : "layers-raw.nrrd");
        byte[] data;
        if (gzip) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(buf)) { gz.write(pixels); }
            data = buf.toByteArray();
        } else {
            data = pixels;
        }
        Files.write(file,concat(header(gzip ? "gzip" : "raw"),data));
        return file;
    }

    private static byte[] header(String encoding) {
        return ("NRRD0005\n" + "type: unsigned char\n" + "dimension: 3\n"
                + "sizes: 40 40 3\n" + "encoding: " + encoding + "\n\n")
                .getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] concat(byte[] a,byte[] b) {
        byte[] all = Arrays.copyOf(a,a.length+b.length);
        System.arraycopy(b,0,all,a.length,b.length);
        return all;
    }

    private static String triangle(int z) {
        return "20,2,"+z+",2,34,"+z+",38,34,"+z+"\n";
    }

    private static String trapezoid(int z) {
        return "10,2,"+z+",30,2,"+z+",2,34,"+z+",38,34,"+z+"\n";
    }

    private static double meanGray(BufferedImage frame) {
        long sum=0;
        for (int y=0;y<frame.getHeight();y++) {
            for (int x=0;x<frame.getWidth();x++) {
                int rgb = frame.getRGB(x,y);
                sum += ((rgb >>> 16) & 255) + ((rgb >>> 8) & 255) + (rgb & 255);
            }
        }
        return sum / (3.0 * frame.getWidth() * frame.getHeight());
    }
}
