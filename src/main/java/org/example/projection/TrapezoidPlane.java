package org.example.projection;

public record TrapezoidPlane(Point3D upperLeft, Point3D upperRight,
                             Point3D lowerLeft, Point3D lowerRight)
        implements PlaneDefinition {
    public TrapezoidPlane {
        java.util.Objects.requireNonNull(upperLeft);
        java.util.Objects.requireNonNull(upperRight);
        java.util.Objects.requireNonNull(lowerLeft);
        java.util.Objects.requireNonNull(lowerRight);
        GeometryValidation.trapezoid(upperLeft,upperRight,lowerLeft,lowerRight);
    }
}
