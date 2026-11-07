package org.example.projection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

/**
 * Repeatable A/B test on the SAME generated input NRRD and CSV.
 * Defaults to the already generated 500^3/120 frame fixture. The familiar
 * FullPipelineBenchmark should be run first to create the inputs.
 *
 * No arguments are needed in IntelliJ; the old run-config issue is irrelevant.
 * Usage: Stage6EncodingABBenchmark [side=500] [frames=120]
 */
public final class Stage6EncodingABBenchmark {
    private Stage6EncodingABBenchmark() { }

    public static void main(String[] args) throws Exception {
        if (args.length > 2) throw new IllegalArgumentException("Usage: Stage6EncodingABBenchmark [side] [frames]");
        int side = args.length >= 1 ? Integer.parseInt(args[0]) : 500;
        int frames = args.length >= 2 ? Integer.parseInt(args[1]) : 120;
        if (side < 32 || side > 500 || frames < 1 || frames > 1000)
            throw new IllegalArgumentException("Invalid side or frames");

        Path dir = Path.of("target", "stage6");
        Path volume = dir.resolve("phantom-v2-" + side + "-uint8.nrrd");
        Path csv = dir.resolve("planes-phantom-v2-smooth-" + side + "-" + frames + ".csv");
        if (!Files.isRegularFile(volume) || !Files.isRegularFile(csv)) {
            throw new IOException("Missing benchmark inputs; first run FullPipelineBenchmark " + side + " " + frames
                    + " so target/stage6 contains the NRRD and CSV. Files expected: " + volume + " and " + csv);
        }
        System.out.println("=== STAGE 6: SAME-DATASET ENCODER A/B COMPARISON ===");
        System.out.printf(Locale.ROOT, "Input: %d^3, %d planes, video 30 FPS%n", side, frames);
        System.out.println("A = legacy AWT/BufferedImage path; B = experimental direct RGB Picture from grayscale path");
        System.out.println("Repeated A/B/B/A to reduce order bias; timings exclude synthetic fixture generation.");
        System.out.println("All MP4 outputs remain in target/stage6; the original MP4 is untouched.");
        String existingMode = System.getProperty("projection.videoMode");
        long[] baseline = new long[2];
        long[] direct = new long[2];
        String[] order = {"awt", "direct-rgb", "direct-rgb", "awt"};
        try {
            for (int iteration = 0; iteration < order.length; iteration++) {
                String mode = order[iteration];
                Path output = dir.resolve("ab-" + side + "-" + frames + "-" + (iteration + 1)
                        + "-" + mode + ".mp4");
                System.out.printf("%n--- RUN %d / 4 (%s) ---%n", iteration + 1, mode);
                System.setProperty("projection.videoMode", mode);
                long start = System.nanoTime();
                int exit = Main.run(new String[]{volume.toString(), csv.toString(), output.toString()},
                        System.out, System.err);
                long time = System.nanoTime() - start;
                if (exit != 0 || !Files.isRegularFile(output) || Files.size(output) == 0) {
                    throw new IllegalStateException("Benchmark run failed in mode " + mode + "; exit code " + exit);
                }
                if (mode.equals("awt")) baseline[iteration == 0 ? 0 : 1] = time;
                else direct[iteration == 1 ? 0 : 1] = time;
                System.out.printf(Locale.ROOT, "WALL CLOCK: %.3f seconds, %.1f frames/s; file: %s%n",
                        time / 1e9, frames * 1e9 / time, output.toAbsolutePath().normalize());
            }
        } finally {
            if (existingMode == null) System.clearProperty("projection.videoMode");
            else System.setProperty("projection.videoMode", existingMode);
        }
        double a = (baseline[0] + baseline[1]) / 2.0;
        double b = (direct[0] + direct[1]) / 2.0;
        System.out.printf(Locale.ROOT, "%nAVERAGE OF TWO RUNS PER MODE%nAWT: %.3f s (%.1f frames/s)%n",
                a / 1e9, frames * 1e9 / a);
        System.out.printf(Locale.ROOT, "Direct RGB Picture: %.3f s (%.1f frames/s)%n", b / 1e9, frames * 1e9 / b);
        System.out.printf(Locale.ROOT, "Ratio (AWT / direct): %.2fx%n", a / b);
        System.out.println("INTERPRETATION: keep AWT by default until Direct RGB Picture is both visually verified and faster.");
        System.out.println("A/B outputs use the same planes; compare frame count, playback, brightness and order.");
    }
}
