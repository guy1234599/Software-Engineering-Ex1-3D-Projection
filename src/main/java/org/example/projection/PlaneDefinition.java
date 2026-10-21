package org.example.projection;

public sealed interface PlaneDefinition permits TrianglePlane, TrapezoidPlane {
    Point3D upperLeft();
    Point3D upperRight();
    Point3D lowerLeft();
    Point3D lowerRight();
}
