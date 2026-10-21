package org.example.projection;

/** Dependency-free basic check; run with -ea. Repeated as JUnit assertions in src/test. */
public final class SmokeTest {
    public static void main(String[] args) {
        short[] voxels=new short[8*8*8];
        for(int z=0;z<8;z++) for(int y=0;y<8;y++) for(int x=0;x<8;x++)
            voxels[x+8*(y+8*z)]=(short)(x+10*y+100*z);
        Volume m=new ShortVolume(8,8,8,voxels);
        PlaneSampler projection=new PlaneSampler(new NearestNeighborSampler());
        Image2D tri=projection.project(m,new TrianglePlane(
            new Point3D(2,0,0),new Point3D(0,4,0),new Point3D(4,4,0)));
        check(tri,new int[][]{{0,0,0,0},{0,12,13,0},{0,22,23,0},{31,32,33,34},{41,42,43,44}});
        Image2D trap=projection.project(m,new TrapezoidPlane(
            new Point3D(1,0,0),new Point3D(3,0,0),new Point3D(0,4,0),new Point3D(4,4,0)));
        check(trap,new int[][]{{0,2,3,0},{0,12,13,0},{21,22,23,24},{31,32,33,34},{41,42,43,44}});
        if (new NearestNeighborSampler().sample(m,7.1,2,3) != m.getVoxel(7,2,3)) throw new AssertionError();
        if (new NearestNeighborSampler().sample(m,7.7,2,3) != 0) throw new AssertionError();
        System.out.println("PASS: both synthetic planes and NN boundary behavior");
    }
    private static void check(Image2D img,int[][] expect) {
        if(img.width()!=expect[0].length || img.height()!=expect.length) throw new AssertionError("Shape mismatch");
        for(int y=0;y<expect.length;y++) for(int x=0;x<expect[y].length;x++) {
            if(img.getUnsigned(x,y)!=expect[y][x])
                throw new AssertionError("Pixel mismatch at ("+x+","+y+"): "+img.getUnsigned(x,y)+" != "+expect[y][x]);
        }
    }
}
