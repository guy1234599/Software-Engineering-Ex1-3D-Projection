package org.example.projection;

abstract class ArrayVolume implements Volume {
    protected final int sx, sy, sz;
    private final int len;

    protected ArrayVolume(int sx, int sy, int sz, int actualLength) {
        if (sx <= 0 || sy <= 0 || sz <= 0 || sx >= 1024 || sy >= 1024 || sz >= 1024) {
            throw new IllegalArgumentException("Volume dimensions must be in [1, 1023]");
        }
        long n = (long)sx * sy * sz;
        if (n != actualLength) throw new IllegalArgumentException("Volume array length does not match dimensions");
        this.sx = sx; this.sy = sy; this.sz = sz; this.len = actualLength;
    }

    @Override public final int sizeX() {return sx;}
    @Override public final int sizeY() {return sy;}
    @Override public final int sizeZ() {return sz;}

    protected final int index(int x, int y, int z) {
        if (x < 0 || x >= sx || y < 0 || y >= sy || z < 0 || z >= sz) {
            throw new IndexOutOfBoundsException("Voxel outside volume");
        }
        return x + sx * (y + sy * z);
    }
}
