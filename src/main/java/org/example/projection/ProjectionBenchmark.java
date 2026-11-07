package org.example.projection;

import java.util.Arrays;
import java.util.Locale;

/**
 * Stage 6 baseline: measures PlaneSampler throughput ONLY, with no disk I/O,
 * grayscale conversion, canvas composition or MP4 encoding.
 *
 * Run from IntelliJ via the green arrow. Defaults: 128^3, 200 measured slices.
 * Optional Program arguments: 500 200
 *
 * Uses real 8-bit ByteVolume storage and preconstructed plane definitions.
 * Do not modify PlaneSampler to fit the benchmark.
 */
public final class ProjectionBenchmark {
    private static volatile long consumedSamples;
    private static final int DEFAULT_SIDE = 128;
    private static final int DEFAULT_FRAMES = 200;

    private ProjectionBenchmark() { }

    public static void main(String[] args) {
        if (args.length > 2) {
            System.err.println("Usage: ProjectionBenchmark [volumeSide=128] [measuredFrames=200]");
            System.exit(2);
        }
        final int side;
        final int frames;
        try {
            side = args.length >= 1 ? Integer.parseInt(args[0]) : DEFAULT_SIDE;
            frames = args.length == 2 ? Integer.parseInt(args[1]) : DEFAULT_FRAMES;
            if (side < 16 || side > 500 || frames < 30 || frames > 1000) {
                throw new IllegalArgumentException("Side must be 16..500; frames must be 30..1000");
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid arguments: " + e.getMessage());
            System.exit(2);
            return;
        }

        Locale.setDefault(Locale.ROOT);
        int warmup = Math.max(30, Math.min(60, frames / 4));
        System.out.println("=== STAGE 6: PROJECTION-ONLY BASELINE ===");
        System.out.printf("Volume: %d x %d x %d, unsigned 8-bit%n", side, side, side);
        System.out.printf("Warm-up: %d slices per scenario; measured: %d per scenario%n", warmup, frames);
        System.out.println("JVM: " + System.getProperty("java.version") + "; OS: " + System.getProperty("os.name"));
        System.out.println("Processor count (JVM): " + Runtime.getRuntime().availableProcessors());
        System.out.printf("Max Java heap: %.1f MiB%n", Runtime.getRuntime().maxMemory() / 1048576.0);
        System.out.println("Timing excludes volume creation, plane construction, image conversion and video encoding.");

        long fillStart = System.nanoTime();
        int voxelCount = Math.multiplyExact(Math.multiplyExact(side, side), side);
        byte[] data = new byte[voxelCount];
        int pos = 0;
        for (int z = 0; z < side; z++) {
            for (int y = 0; y < side; y++) {
                int base = 7 * y + 11 * z;
                for (int x = 0; x < side; x++) {
                    data[pos++] = (byte) (base + 3 * x);
                }
            }
        }
        Volume volume = new ByteVolume(side, side, side, data);
        double creationMs = (System.nanoTime() - fillStart) / 1_000_000.0;
        System.out.printf("Synthetic volume initialized in %.1f ms (data %.1f MiB)%n",
                creationMs, data.length / 1048576.0);

        PlaneSampler projector = new PlaneSampler(new NearestNeighborSampler());
        runCase("Axis-aligned trapezoid", volume, projector, side, frames, warmup, 0);
        runCase("Oblique trapezoid", volume, projector, side, frames, warmup, 1);
        runCase("Axis-aligned triangle", volume, projector, side, frames, warmup, 2);
        System.out.println("Checksum: " + consumedSamples + " (prevents results being ignored)");
        System.out.println("Interpretation: 100 FPS corresponds to 10 ms/slice; 30 FPS to 33.33 ms/slice.");
        System.out.println("Use the same size, frame count, JVM and machine when comparing optimizations.");
    }

    private static void runCase(String label, Volume volume, PlaneSampler projector,
                                int side, int frames, int warmup, int kind) {
        PlaneDefinition[] planes = new PlaneDefinition[warmup + frames];
        for (int i = 0; i < planes.length; i++) {
            planes[i] = makePlane(side, i, kind);
        }
        long[] samples = new long[frames];
        long checksum = 0;
        for (int i = 0; i < planes.length; i++) {
            long start = System.nanoTime();
            Image2D slice = projector.project(volume, planes[i]);
            long elapsed = System.nanoTime() - start;
            // Read a real computed pixel outside the timed region.
            checksum += slice.getUnsigned(slice.width() / 2, slice.height() / 2);
            if (i >= warmup) samples[i - warmup] = elapsed;
        }
        consumedSamples += checksum;
        long total = 0;
        for (long nanos : samples) total += nanos;
        Arrays.sort(samples);
        double meanMs = total / (frames * 1_000_000.0);
        double medianMs = samples[(frames - 1) / 2] / 1_000_000.0;
        double p95Ms = samples[(int) Math.ceil(frames * .95) - 1] / 1_000_000.0;
        double fps = 1_000_000_000.0 * frames / total;
        System.out.printf("%-24s  mean=%.3f ms  median=%.3f ms  p95=%.3f ms  avg=%.1f FPS%n",
                label, meanMs, medianMs, p95Ms, fps);
    }

    private static PlaneDefinition makePlane(int side, int i, int kind) {
        double max = side - 1;
        int margin = Math.max(2, side / 8);
        double z = (i * 17) % side;
        if (kind == 2) {
            return new TrianglePlane(
                    new Point3D(max / 2.0, 0, z),
                    new Point3D(0, max, z),
                    new Point3D(max, max, z));
        }
        if (kind == 1) {
            // Both edges move equally in Z: still coplanar and isosceles.
            double upperZ = (i * 17) % (side / 2);
            double lowerZ = upperZ + (side - 1) / 3.0;
            return new TrapezoidPlane(
                    new Point3D(margin, 0, upperZ),
                    new Point3D(max - margin, 0, upperZ),
                    new Point3D(0, max, lowerZ),
                    new Point3D(max, max, lowerZ));
        }
        return new TrapezoidPlane(
                new Point3D(margin, 0, z),
                new Point3D(max - margin, 0, z),
                new Point3D(0, max, z),
                new Point3D(max, max, z));
    }
}
