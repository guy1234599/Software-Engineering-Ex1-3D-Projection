package org.example.projection;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

/** Command line entry point: input.nrrd planes.csv output.mp4 */
public final class Main {
    private static final int FPS = 30;
    // Windows H.264 decoders may reject extremely small video streams.
    // A safe video canvas floor does not change the projection sampling grid.
    private static final int MIN_VIDEO_SIZE = 128;
    private static final long MAX_VIDEO_PIXELS = 25_000_000L;

    private Main() { }

    public static void main(String[] args) {
        // IntelliJ's gutter Run action usually supplies no program arguments.
        // In that case, launch the included synthetic demo instead of printing Usage.
        // Explicit CLI arguments are left unchanged for normal production use.
        if (args != null && args.length == 0) {
            args = new String[]{
                    "samples/synthetic-32x32x32.nrrd",
                    "samples/planes-90.csv",
                    "target/synthetic-90-direct.mp4"
            };
            System.out.println("No arguments supplied: running the built-in 90-frame demo.");
            System.out.println("Working directory: " + Path.of("").toAbsolutePath().normalize());
        }
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) System.exit(exitCode);
    }

    /** Returns an exit code, making the command-line workflow testable without System.exit. */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (args == null || args.length != 3) {
            err.println("Usage: Main <volume.nrrd> <planes.csv> <output.mp4>");
            return 2;
        }

        Path stagedOutput = null;
        try {
            Path nrrd = Path.of(args[0]).toAbsolutePath().normalize();
            Path csv = Path.of(args[1]).toAbsolutePath().normalize();
            Path output = Path.of(args[2]).toAbsolutePath().normalize();
            if (!output.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mp4")) {
                throw new IllegalArgumentException("Output path must end with .mp4");
            }
            if (output.equals(nrrd) || output.equals(csv)) {
                throw new IllegalArgumentException("Output path must differ from input paths");
            }

            // Validate every CSV record before loading the volume or touching the output.
            List<PlaneDefinition> planes = new CsvPlaneReader().read(csv);
            CanvasSize canvas = determineCanvas(planes);

            long startLoad = System.nanoTime();
            Volume volume = new NrrdReader().read(nrrd);
            IntensityMapper intensities = IntensityMapper.fromVolume(volume);
            long loadNanos = System.nanoTime() - startLoad;
            PlaneSampler projector = new PlaneSampler(new NearestNeighborSampler());

            Path parent = output.getParent();
            Files.createDirectories(parent);
            // Staging avoids publishing a partial video when a late projection fails.
            stagedOutput = Files.createTempFile(parent, ".volume-projection-", ".mp4");

            long projectionNanos = 0;
            long writeNanos = 0;
            try (VideoWriter writer = new VideoWriter(stagedOutput, canvas.width(), canvas.height(), FPS)) {
                for (PlaneDefinition plane : planes) {
                    long t0 = System.nanoTime();
                    Image2D slice = projector.project(volume, plane);
                    projectionNanos += System.nanoTime() - t0;

                    long t1 = System.nanoTime();
                    BufferedImage gray = intensities.toBufferedImage(slice);
                    BufferedImage frame = FrameComposer.compose(gray, canvas.width(), canvas.height());
                    writer.writeFrame(frame);
                    writeNanos += System.nanoTime() - t1;
                }
                if (writer.frameCount() != planes.size()) {
                    throw new IOException("Generated frame count does not match CSV planes");
                }
            }

            publish(stagedOutput, output);
            stagedOutput = null;
            out.printf(Locale.ROOT, "Loaded volume: %d x %d x %d%n",
                    volume.sizeX(), volume.sizeY(), volume.sizeZ());
            out.printf(Locale.ROOT, "Frames: %d, video: %d x %d, playback: %d FPS%n",
                    planes.size(), canvas.width(), canvas.height(), FPS);
            out.printf(Locale.ROOT, "Projection: %.2f ms total, %.1f FPS (excluding image conversion and video encoding)%n",
                    projectionNanos / 1_000_000.0, fps(planes.size(), projectionNanos));
            out.printf(Locale.ROOT, "Frame conversion + encoding: %.2f ms total%n", writeNanos / 1_000_000.0);
            out.printf(Locale.ROOT, "Volume loading + range scan: %.2f ms%n", loadNanos / 1_000_000.0);
            out.println("Created: " + output);
            return 0;
        } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
            err.println("ERROR: " + ex.getMessage());
            return 1;
        } finally {
            if (stagedOutput != null) {
                try { Files.deleteIfExists(stagedOutput); }
                catch (IOException ex) { err.println("WARNING: could not delete temporary video: " + stagedOutput); }
            }
        }
    }

    private static double fps(int count, long nanos) {
        return nanos == 0 ? Double.POSITIVE_INFINITY : (1_000_000_000.0 * count / nanos);
    }

    /**
     * Compute an output canvas before encoding the first frame. The equations
     * intentionally match PlaneSampler's current width/height rounding rules.
     * We check this contract in MainTest to catch future changes to PlaneSampler.
     */
    static CanvasSize determineCanvas(List<PlaneDefinition> planes) {
        int maxWidth = 1;
        int maxHeight = 1;
        for (PlaneDefinition plane : planes) {
            Point3D upperLeft = plane.upperLeft();
            Point3D upperRight = plane.upperRight();
            Point3D lowerLeft = plane.lowerLeft();
            Point3D lowerRight = plane.lowerRight();

            double topWidth = upperLeft.distance(upperRight);
            double bottomWidth = lowerLeft.distance(lowerRight);
            Point3D u = lowerRight.minus(lowerLeft).times(1.0 / bottomWidth);
            Point3D topCenter = upperLeft.plus(upperRight).times(0.5);
            Point3D bottomCenter = lowerLeft.plus(lowerRight).times(0.5);
            Point3D q = bottomCenter.minus(topCenter);
            double d = q.dot(u);
            double h = q.minus(u.times(d)).length();
            double D = lowerLeft.distance(upperLeft);
            double xMin = Math.min(-topWidth / 2, d - bottomWidth / 2);
            double xMax = Math.max(topWidth / 2, d + bottomWidth / 2);
            double span = xMax - xMin;

            if (!Double.isFinite(h) || h <= 0 || !Double.isFinite(D) || D <= 0
                    || !Double.isFinite(span) || span <= 0
                    || D > Integer.MAX_VALUE - 1.0 || span > Integer.MAX_VALUE - 1.0) {
                throw new IllegalArgumentException("Invalid dimensions for a plane");
            }
            int width = Math.max(1, (int) Math.ceil(span));
            int height = Math.max(1, (int) Math.ceil(D));
            if ((long) width * height > MAX_VIDEO_PIXELS) {
                throw new IllegalArgumentException("A slice exceeds the 25,000,000-pixel safety limit");
            }
            maxWidth = Math.max(maxWidth, width);
            maxHeight = Math.max(maxHeight, height);
        }
        int canvasWidth = FrameComposer.evenSize(Math.max(MIN_VIDEO_SIZE, maxWidth));
        int canvasHeight = FrameComposer.evenSize(Math.max(MIN_VIDEO_SIZE, maxHeight));
        if ((long) canvasWidth * canvasHeight > MAX_VIDEO_PIXELS) {
            throw new IllegalArgumentException("Video canvas exceeds the 25,000,000-pixel safety limit");
        }
        return new CanvasSize(canvasWidth, canvasHeight);
    }

    private static void publish(Path staged, Path target) throws IOException {
        try {
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    record CanvasSize(int width, int height) { }
}
