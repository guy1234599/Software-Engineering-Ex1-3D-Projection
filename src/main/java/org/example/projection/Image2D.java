package org.example.projection;

public final class Image2D {
    private final int width,height;
    private final int[] pixels;

    public Image2D(int width,int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Image dimensions must be positive");
        this.width=width;this.height=height;
        pixels=new int[Math.multiplyExact(width,height)];
    }
    public int width() {return width;}
    public int height() {return height;}
    public void set(int x,int y,long unsignedValue) {
        if (unsignedValue < 0 || unsignedValue > 0xFFFF_FFFFL)
            throw new IllegalArgumentException("Sample must be unsigned 32-bit");
        pixels[Math.addExact(Math.multiplyExact(y,width),x)]=(int)unsignedValue;
    }
    public long getUnsigned(int x,int y) {
        if (x<0 || x>=width || y<0 || y>=height) throw new IndexOutOfBoundsException();
        return Integer.toUnsignedLong(pixels[y*width+x]);
    }
}
