package org.example.projection;

public interface Volume {
    int sizeX();
    int sizeY();
    int sizeZ();
    // Unsigned, nonnegative value; valid integer indices required.
    long getVoxel(int x, int y, int z);
}
