package org.example.projection;

public record TrianglePlane(Point3D apex, Point3D baseLeft, Point3D baseRight)
        implements PlaneDefinition {
    public TrianglePlane {
        java.util.Objects.requireNonNull(apex);
        java.util.Objects.requireNonNull(baseLeft);
        java.util.Objects.requireNonNull(baseRight);
        GeometryValidation.triangle(apex,baseLeft,baseRight);
    }
    @Override public Point3D upperLeft() {return apex;}
    @Override public Point3D upperRight() {return apex;}
    @Override public Point3D lowerLeft() {return baseLeft;}
    @Override public Point3D lowerRight() {return baseRight;}
}
