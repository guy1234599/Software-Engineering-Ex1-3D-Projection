package org.example.projection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Checks that the real Main pipeline reports separate timings without losing frames. */
class Stage6TimingBreakdownTest {
    @TempDir Path dir;

    @Test
    void fullPipelineReportsTimingBreakdown() throws Exception {
        Path nrrd = dir.resolve("tiny.nrrd");
        byte[] header = ("NRRD0005\n" +
                "type: unsigned char\n" +
                "dimension: 3\n" +
                "sizes: 2 2 2\n" +
                "encoding: raw\n\n").getBytes(StandardCharsets.US_ASCII);
        byte[] raw = new byte[header.length + 8];
        System.arraycopy(header, 0, raw, 0, header.length);
        for (int i = 0; i < 8; ++i) raw[header.length + i] = (byte) (i * 30);
        Files.write(nrrd, raw);

        Path csv = dir.resolve("planes.csv");
        Files.writeString(csv, "0.5,0,0,0,1,0,1,1,0\n" +
                "0.2,0,0,0.8,0,0,0,1,0,1,1,0\n");
        Path mp4 = dir.resolve("output.mp4");

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
        int exit = Main.run(new String[]{nrrd.toString(), csv.toString(), mp4.toString()},
                new PrintStream(captured, true, StandardCharsets.UTF_8),
                new PrintStream(capturedErr, true, StandardCharsets.UTF_8));
        String output = captured.toString(StandardCharsets.UTF_8);
        assertEquals(0, exit, capturedErr.toString(StandardCharsets.UTF_8));
        assertTrue(Files.isRegularFile(mp4));
        assertTrue(Files.size(mp4) > 100);
        assertTrue(output.contains("Frames: 2, video: 128 x 128, playback: 30 FPS"));

        double gray = milliseconds(output, "Grayscale conversion");
        double compose = milliseconds(output, "Frame composition");
        double encode = milliseconds(output, "Video encoding (JCodec writeFrame)");
        double finish = milliseconds(output, "MP4 finalization (writer.close)");
        double combined = milliseconds(output, "Frame conversion + encoding");
        assertTrue(gray >= 0 && compose >= 0 && encode >= 0 && finish >= 0);
        assertEquals(gray + compose + encode, combined, 0.04,
                "Combined time should match the individual measurements, within rounding error");
    }

    private static double milliseconds(String log, String label) {
        Pattern pattern = Pattern.compile("(?m)^" + Pattern.quote(label) + ": ([0-9]+\\.[0-9]{2}) ms(?: total)?$");
        Matcher found = pattern.matcher(log);
        assertTrue(found.find(), () -> "Missing timing label: " + label + "\n" + log);
        return Double.parseDouble(found.group(1));
    }
}
