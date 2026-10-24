package org.example.projection;

import java.nio.file.Path;
import java.util.List;

/** Demonstrates NRRD + CSV + projection together, without video encoding. */
public final class PlanePipelineDemo {
    private PlanePipelineDemo() { }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: PlanePipelineDemo <volume.nrrd> <planes.csv>");
            System.exit(2);
        }
        try {
            // Validate all plane records before doing any projection work.
            List<PlaneDefinition> planes = new CsvPlaneReader().read(Path.of(args[1]));
            Volume volume = new NrrdReader().read(Path.of(args[0]));
            PlaneSampler projector = new PlaneSampler(new NearestNeighborSampler());
            System.out.printf("Loaded volume %d x %d x %d; %d plane(s)%n",
                    volume.sizeX(), volume.sizeY(), volume.sizeZ(), planes.size());
            for (int i = 0; i < planes.size(); i++) {
                Image2D image = projector.project(volume, planes.get(i));
                System.out.printf("Frame %d: %d x %d%n", i + 1, image.width(), image.height());
            }
            System.out.println("Projection pipeline OK (MP4 not implemented yet)");
        } catch (Exception ex) {
            System.err.println("Pipeline error: " + ex.getMessage());
            System.exit(1);
        }
    }
}
