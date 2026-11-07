package org.example.projection;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Stage 6: measure a real NRRD -> CSV -> slice -> gray -> MP4 run, using Main.run.
 *
 * Usage from IntelliJ, Program arguments: [side=128] [frames=90] [smooth|mixed]
 * Recommended large run: 500 120
 * Default smooth mode produces coherent axial slices through a 3D phantom.
 * The optional mixed mode retains varied plane geometry for a stress benchmark.
 *
 * Test data lives under target/stage6, so it is not committed to Git.
 * It is deliberately generated OUTSIDE the measured end-to-end interval.
 * No changes to the production pipeline are required.
 */
public final class FullPipelineBenchmark {
    private static final int DEFAULT_SIDE = 128;
    private static final int DEFAULT_FRAMES = 90;
    private static final int BUFFER_SIZE = 256 * 1024;

    private FullPipelineBenchmark() { }

    public static void main(String[] args) {
        try {
            if (args.length > 3) {
                throw new IllegalArgumentException("Usage: FullPipelineBenchmark [side=128] [frames=90] [smooth|mixed]");
            }
            int side = args.length >= 1 ? Integer.parseInt(args[0]) : DEFAULT_SIDE;
            int frames = args.length >= 2 ? Integer.parseInt(args[1]) : DEFAULT_FRAMES;
            if (side < 32 || side > 500 || frames < 30 || frames > 1000) {
                throw new IllegalArgumentException("Side must be 32..500 and frames 30..1000");
            }
            String mode = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "smooth";
            if (!mode.equals("smooth") && !mode.equals("mixed")) {
                throw new IllegalArgumentException("Mode must be smooth or mixed");
            }
            run(side, frames, mode, System.out);
        } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
            System.err.println("BENCHMARK FAILED: " + ex.getMessage());
            System.exit(1);
        }
    }

    private static void run(int side, int frames, String mode, PrintStream out) throws IOException {
        Path dir = Path.of("target", "stage6");
        Files.createDirectories(dir);
        // Versioned filenames ensure the older striped fixtures cannot be accidentally reused.
        Path volume = dir.resolve("phantom-v2-" + side + "-uint8.nrrd");
        Path csv = dir.resolve("planes-phantom-v2-" + mode + "-" + side + "-" + frames + ".csv");
        Path video = dir.resolve("pipeline-phantom-v2-" + mode + "-" + side + "-" + frames + ".mp4");

        out.println("=== STAGE 6: FULL PIPELINE BENCHMARK ===");
        out.printf(Locale.ROOT, "Volume: %d x %d x %d, 8-bit; planes: %d; playback: 30 FPS%n",
                side, side, side, frames);
        out.printf("Fixture: 3D phantom v2; plane sequence: %s%n", mode);
        out.printf(Locale.ROOT, "Max heap: %.1f MiB; Java: %s%n",
                Runtime.getRuntime().maxMemory() / 1048576.0,
                System.getProperty("java.version"));
        out.println("Fixture generation is OUTSIDE the timed pipeline interval.");

        long fixtureStart = System.nanoTime();
        boolean created = createNrrdIfNeeded(volume, side);
        createCsv(csv, side, frames, mode);
        double fixtureSeconds = (System.nanoTime() - fixtureStart) / 1e9;
        out.printf(Locale.ROOT, "Fixtures %s in %.3f s; input NRRD: %.1f MiB%n",
                created ? "generated" : "reused (header/size checked)",
                fixtureSeconds, Files.size(volume) / 1048576.0);
        out.println("Input: " + volume.toAbsolutePath().normalize());
        out.println("CSV:   " + csv.toAbsolutePath().normalize());

        // Main.run is the real entry-point pipeline, not a second implementation.
        long start = System.nanoTime();
        int exitCode = Main.run(new String[]{volume.toString(), csv.toString(), video.toString()},
                out, System.err);
        double pipelineSeconds = (System.nanoTime() - start) / 1e9;
        if (exitCode != 0) {
            throw new IllegalStateException("Main.run returned exit code " + exitCode);
        }
        if (!Files.isRegularFile(video) || Files.size(video) == 0L) {
            throw new IOException("Main returned success but MP4 file is absent or empty: " + video);
        }
        out.println("--- WALL-CLOCK RESULT (includes CSV, load, conversion, MP4 finalization) ---");
        out.printf(Locale.ROOT, "End-to-end time: %.3f seconds%n", pipelineSeconds);
        out.printf(Locale.ROOT, "End-to-end throughput: %.1f frames/second%n", frames / pipelineSeconds);
        out.printf(Locale.ROOT, "Playback duration: %.2f seconds (= %d frames / 30 FPS)%n",
                frames / 30.0, frames);
        out.printf(Locale.ROOT, "Output MP4 size: %.2f MiB%n", Files.size(video) / 1048576.0);
        out.println("Output: " + video.toAbsolutePath().normalize());
        out.println("NOTE: Playback 30 FPS is NOT the same as processing throughput.");
        out.println("NOTE: This is one end-to-end run; repeat to assess run-to-run variation.");
    }

    /**
     * Write a deterministic 3D phantom (ellipsoidal tissue, bright rim, two dark cavities,
     * and a localized bright nodule). Each axial slice changes gradually with z.
     * The phantom is illustrative and NOT real patient data.
     *
     * NRRD is attached raw uint8; x is the fastest-varying axis. Data generation is outside
     * the timed benchmark and uses only a bounded streaming buffer.
     */
    private static boolean createNrrdIfNeeded(Path file, int side) throws IOException {
        String header = "NRRD0005\n" +
                "# Stage 6 analytic ellipsoid phantom, version 2 (NOT medical imaging)\n" +
                "type: uint8\n" +
                "dimension: 3\n" +
                "sizes: " + side + " " + side + " " + side + "\n" +
                "encoding: raw\n\n";
        byte[] prefix = header.getBytes(StandardCharsets.US_ASCII);
        long expectedSize = prefix.length + (long) side * side * side;
        if (Files.isRegularFile(file) && Files.size(file) == expectedSize
                && headerMatches(file, prefix)) {
            return false;
        }

        // Normalized coordinates: the phantom spans most of the physical volume.
        double[] ux = new double[side];
        double[] vy = new double[side];
        double[] wz = new double[side];
        double center = (side - 1.0) / 2.0;
        for (int n = 0; n < side; n++) {
            ux[n] = (n - center) / (0.43 * side);
            vy[n] = (n - center) / (0.43 * side);
            wz[n] = (n - center) / (0.48 * side);
        }

        Path temp = Files.createTempFile(file.getParent(), "phantom-v2-", ".part");
        try {
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(temp), BUFFER_SIZE)) {
                out.write(prefix);
                byte[] buffer = new byte[BUFFER_SIZE];
                int index = 0;
                for (int z = 0; z < side; z++) {
                    double w = wz[z];
                    double w2 = w * w;
                    for (int y = 0; y < side; y++) {
                        double v = vy[y];
                        double yz2 = v * v + w2;
                        for (int x = 0; x < side; x++) {
                            double u = ux[x];
                            double r2 = u * u + yz2;
                            int gray = 0;
                            if (r2 < 1.0) {
                                // Anatomical-looking but purely synthetic intensity regions.
                                gray = 68 + (int) (62.0 * (1.0 - r2)) + (int) (11.0 * (0.5 - v));
                                if (r2 > 0.90) gray = 185; // outer bright rim
                                else if (r2 > 0.81) gray = 117; // darker outer band
                                else if (r2 > 0.60 && r2 < 0.64) gray = 160; // inner contour

                                // Two small dark cavities visible through the middle slices.
                                double leftX = (u + 0.23) / 0.17;
                                double rightX = (u - 0.23) / 0.17;
                                double pocketY = (v + 0.05) / 0.27;
                                double pocketZ = w / 0.61;
                                double cavityYZ = pocketY * pocketY + pocketZ * pocketZ;
                                if (leftX * leftX + cavityYZ < 1.0 ||
                                        rightX * rightX + cavityYZ < 1.0) {
                                    gray = 24;
                                }

                                // Bright spherical landmark moves through consecutive z slices.
                                double nodX = (u - 0.49) / 0.20;
                                double nodY = (v - 0.27) / 0.23;
                                double nodZ = (w - 0.13) / 0.31;
                                if (nodX * nodX + nodY * nodY + nodZ * nodZ < 1.0) {
                                    gray = 237;
                                }
                            }
                            buffer[index++] = (byte) gray;
                            if (index == buffer.length) {
                                out.write(buffer);
                                index = 0;
                            }
                        }
                    }
                }
                if (index != 0) out.write(buffer, 0, index);
            }
            if (Files.size(temp) != expectedSize) {
                throw new IOException("Generated NRRD has an unexpected size");
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static boolean headerMatches(Path file, byte[] expected) throws IOException {
        try (var in = Files.newInputStream(file)) {
            byte[] actual = in.readNBytes(expected.length);
            return java.util.Arrays.equals(expected, actual);
        }
    }

    /** Generate a coherent axial scan by default; mixed mode is a geometry stress test. */
    private static void createCsv(Path csv, int side, int frames, String mode) throws IOException {
        double max = side - 1.0;
        double margin = Math.max(3.0, side * 0.045);
        StringBuilder rows = new StringBuilder(frames * 130);
        for (int i = 0; i < frames; i++) {
            if (mode.equals("smooth")) {
                // Consecutive, parallel planes progress monotonically through the phantom.
                // Every frame has the same shape and orientation; only its depth changes.
                double startZ = 0.07 * max;
                double z = startZ + (max - 2.0 * startZ) * i / (frames - 1.0);
                addPoint(rows, margin, 0, z);
                addPoint(rows, max - margin, 0, z);
                addPoint(rows, 0, max, z);
                addPoint(rows, max, max, z);
            } else {
                // Optional stress mode: deliberately mixes planes of different orientations.
                // NOT suitable for demonstrating continuous medical-like slices.
                double z = (i * 17L) % side;
                if (i % 3 == 2) {
                    addPoint(rows, max / 2.0, 0, z);
                    addPoint(rows, 0, max, z);
                    addPoint(rows, max, max, z);
                } else if (i % 3 == 1) {
                    double upperZ = (i * 17L) % (side / 2);
                    double lowerZ = upperZ + max / 3.0;
                    addPoint(rows, margin, 0, upperZ);
                    addPoint(rows, max - margin, 0, upperZ);
                    addPoint(rows, 0, max, lowerZ);
                    addPoint(rows, max, max, lowerZ);
                } else {
                    addPoint(rows, margin, 0, z);
                    addPoint(rows, max - margin, 0, z);
                    addPoint(rows, 0, max, z);
                    addPoint(rows, max, max, z);
                }
            }
            rows.setCharAt(rows.length() - 1, '\n'); // replace trailing comma
        }
        Files.writeString(csv, rows.toString(), StandardCharsets.US_ASCII);
    }

    private static void addPoint(StringBuilder sb, double x, double y, double z) {
        sb.append(String.format(Locale.ROOT, "%.8f,%.8f,%.8f,", x, y, z));
    }
}
