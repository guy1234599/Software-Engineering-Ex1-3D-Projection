package org.example.projection;

public final class ByteVolume extends ArrayVolume {
    private final byte[] data;
    // Ownership is transferred; caller must not mutate data after construction.
    public ByteVolume(int sx, int sy, int sz, byte[] data) {
        super(sx,sy,sz,java.util.Objects.requireNonNull(data).length);
        this.data = data;
    }
    @Override public long getVoxel(int x, int y, int z) {
        return Byte.toUnsignedInt(data[index(x,y,z)]);
    }
}
