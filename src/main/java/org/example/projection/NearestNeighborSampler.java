package org.example.projection;

public final class NearestNeighborSampler implements VoxelSampler {
    @Override public long sample(Volume volume, double x, double y, double z) {
        // Continuous point outside [0,size) must yield 0, even if Math.round would enter the array.
        if (!(x >= 0 && x < volume.sizeX() && y >= 0 && y < volume.sizeY()
                && z >= 0 && z < volume.sizeZ())) return 0;
        long ix = Math.round(x), iy = Math.round(y), iz = Math.round(z);
        if (ix >= volume.sizeX() || iy >= volume.sizeY() || iz >= volume.sizeZ()) return 0;
        return volume.getVoxel((int)ix,(int)iy,(int)iz);
    }
}
