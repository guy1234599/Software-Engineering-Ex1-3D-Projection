package org.example.projection;

import java.nio.file.Path;

/** A small independent entry point for manually checking NRRD loading. */
public final class NrrdReaderDemo {
    private NrrdReaderDemo() { }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: NrrdReaderDemo <path-to-attached.nrrd>");
            System.exit(2);
        }
        try {
            Volume volume = new NrrdReader().read(Path.of(args[0]));
            System.out.printf("Loaded NRRD: %d x %d x %d (%s)%n",
                    volume.sizeX(), volume.sizeY(), volume.sizeZ(),
                    volume.getClass().getSimpleName());
            System.out.printf("First voxel M[0][0][0] = %d%n", volume.getVoxel(0,0,0));
            System.out.printf("Last voxel  = %d%n", volume.getVoxel(
                    volume.sizeX()-1, volume.sizeY()-1, volume.sizeZ()-1));
        } catch (Exception ex) {
            System.err.println("NRRD error: " + ex.getMessage());
            System.exit(1);
        }
    }
}
