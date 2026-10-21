package org.example.projection;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectionTest {
    @Test void triangleMatchesSyntheticReference() {
        Volume v=volume();
        Image2D img=new PlaneSampler(new NearestNeighborSampler()).project(v,
            new TrianglePlane(new Point3D(2,0,0),new Point3D(0,4,0),new Point3D(4,4,0)));
        assertEquals(4,img.width());assertEquals(5,img.height());
        long[][] expected={{0,0,0,0},{0,12,13,0},{0,22,23,0},{31,32,33,34},{41,42,43,44}};
        for(int y=0;y<5;y++) for(int x=0;x<4;x++) assertEquals(expected[y][x],img.getUnsigned(x,y));
    }
    @Test void trapezoidMatchesSyntheticReference() {
        Image2D img=new PlaneSampler(new NearestNeighborSampler()).project(volume(),
            new TrapezoidPlane(new Point3D(1,0,0),new Point3D(3,0,0),new Point3D(0,4,0),new Point3D(4,4,0)));
        long[][] expected={{0,2,3,0},{0,12,13,0},{21,22,23,24},{31,32,33,34},{41,42,43,44}};
        for(int y=0;y<5;y++) for(int x=0;x<4;x++) assertEquals(expected[y][x],img.getUnsigned(x,y));
    }
    @Test void unsigned32AndBoundaries() {
        Volume v=new IntVolume(2,1,1,new int[]{-1,0});
        assertEquals(4_294_967_295L,v.getVoxel(0,0,0));
        NearestNeighborSampler nn=new NearestNeighborSampler();
        assertEquals(4_294_967_295L,nn.sample(v,0,0,0));
        assertEquals(0L,nn.sample(v,-0.1,0,0));
        assertEquals(0L,nn.sample(v,2.0,0,0));
    }
    @Test void rejectsInvalidTriangle() {
        assertThrows(IllegalArgumentException.class, () -> new TrianglePlane(
            new Point3D(0,0,0),new Point3D(1,1,0),new Point3D(4,1,0)));
    }
    private Volume volume() {
        short[] a=new short[8*8*8];
        for(int z=0;z<8;z++) for(int y=0;y<8;y++) for(int x=0;x<8;x++)
            a[x+8*(y+8*z)]=(short)(x+10*y+100*z);
        return new ShortVolume(8,8,8,a);
    }
}
