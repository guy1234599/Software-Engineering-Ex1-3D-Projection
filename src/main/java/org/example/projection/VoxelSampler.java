package org.example.projection;

public interface VoxelSampler {
    long sample(Volume volume, double x, double y, double z);
}
