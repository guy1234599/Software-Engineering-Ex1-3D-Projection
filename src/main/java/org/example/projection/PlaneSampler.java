package org.example.projection;

import java.util.Objects;

public final class PlaneSampler {
    private final VoxelSampler sampler;
    // Starter safety cap: adjust according to available memory and documented acceptance limits.
    private static final long MAX_PIXELS = 25_000_000L;
    private static final double PIXEL_EPS = 1e-9;

    public PlaneSampler(VoxelSampler sampler) {this.sampler=Objects.requireNonNull(sampler);}

    public Image2D project(Volume volume, PlaneDefinition plane) {
        Objects.requireNonNull(volume); Objects.requireNonNull(plane);
        Point3D u0=plane.upperLeft(),u1=plane.upperRight();
        Point3D l0=plane.lowerLeft(),l1=plane.lowerRight();
        double w0=u0.distance(u1),w1=l0.distance(l1);
        Point3D u=l1.minus(l0).times(1.0/w1);
        Point3D m0=u0.plus(u1).times(0.5);
        Point3D m1=l0.plus(l1).times(0.5);
        Point3D q=m1.minus(m0);
        double d=q.dot(u);
        Point3D qPerp=q.minus(u.times(d));
        double h=qPerp.length();
        if (!Double.isFinite(h) || h <= 0) throw new IllegalArgumentException("Zero/invalid patch height");
        Point3D v=qPerp.times(1.0/h);
        double D=l0.distance(u0);

        double xMin=Math.min(-w0/2,d-w1/2),xMax=Math.max(w0/2,d+w1/2);
        double span=xMax-xMin;
        if (!Double.isFinite(span) || !Double.isFinite(D) || span <= 0 || D <= 0
            || span > Integer.MAX_VALUE || D > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid projected image dimensions");
        int W=(int)Math.max(1,Math.ceil(span));
        int H=(int)Math.max(1,Math.ceil(D));
        if ((long)W*H>MAX_PIXELS)
            throw new IllegalArgumentException("Image exceeds starter pixel limit: "+MAX_PIXELS);
        Image2D img=new Image2D(W,H);
        double padX=(W-span)/2.0, padY=(H-h)/2.0;
        double sStart=xMin+0.5-padX;

        for(int y=0;y<H;y++) {
            double t=y+0.5-padY;
            if (t<0 || t>h) continue;
            double center=d*t/h;
            double halfWidth=(w0+(w1-w0)*t/h)/2;
            double minIndex=center-halfWidth-xMin-0.5+padX;
            double maxIndex=center+halfWidth-xMin-0.5+padX;
            int first=(int)Math.max(0,Math.ceil(minIndex-PIXEL_EPS));
            int last=(int)Math.min(W-1,Math.floor(maxIndex+PIXEL_EPS));
            // Avoid per-pixel 3D objects.
            double s=sStart+first;
            double px=m0.x()+t*v.x()+s*u.x();
            double py=m0.y()+t*v.y()+s*u.y();
            double pz=m0.z()+t*v.z()+s*u.z();
            for(int x=first;x<=last;x++) {
                img.set(x,y,sampler.sample(volume,px,py,pz));
                px+=u.x();py+=u.y();pz+=u.z();
            }
        }
        return img;
    }
}
