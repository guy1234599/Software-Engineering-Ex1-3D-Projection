package org.example.projection;

public record Point3D(double x, double y, double z) {
    public Point3D {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new IllegalArgumentException("Point coordinates must be finite");
    }
    public Point3D minus(Point3D p) {return new Point3D(x-p.x,y-p.y,z-p.z);}
    public Point3D plus(Point3D p) {return new Point3D(x+p.x,y+p.y,z+p.z);}
    public Point3D times(double t) {return new Point3D(x*t,y*t,z*t);}
    public double dot(Point3D p) {return x*p.x+y*p.y+z*p.z;}
    public Point3D cross(Point3D p) {return new Point3D(y*p.z-z*p.y,z*p.x-x*p.z,x*p.y-y*p.x);}
    public double length() {return Math.sqrt(x*x+y*y+z*z);}
    public double distance(Point3D p) {return minus(p).length();}
}
