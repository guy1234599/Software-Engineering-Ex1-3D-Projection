package org.example.projection;

final class GeometryValidation {
    static final double EPS = 1e-6; // Candidate tolerance; to be benchmarked/tested.
    private GeometryValidation() {}

    static boolean near(double a, double b) {
        return Math.abs(a-b) <= EPS * Math.max(1, Math.max(Math.abs(a), Math.abs(b)));
    }
    static void triangle(Point3D a, Point3D b, Point3D c) {
        double ab = a.distance(b), ac = a.distance(c), bc = b.distance(c);
        if (ab <= EPS || bc <= EPS || !near(ab,ac))
            throw new IllegalArgumentException("Triangle requires two equal positive legs and a positive base");
        Point3D cross = b.minus(a).cross(c.minus(a));
        if (cross.length() <= EPS * ab * ac)
            throw new IllegalArgumentException("Triangle is degenerate (collinear points)");
    }
    static void trapezoid(Point3D a1, Point3D a2, Point3D b, Point3D c) {
        Point3D top = a2.minus(a1), bottom = c.minus(b);
        double w0=top.length(), w1=bottom.length();
        double left=a1.distance(b), right=a2.distance(c);
        if (w0<=EPS || w1<=EPS || left<=EPS || !near(left,right))
            throw new IllegalArgumentException("Trapezoid requires positive parallel bases and equal positive legs");
        if (top.dot(bottom) <= 0 || top.cross(bottom).length() > EPS*w0*w1)
            throw new IllegalArgumentException("Trapezoid bases must be parallel and consistently ordered");
        Point3D n=top.cross(b.minus(a1));
        if (n.length() <= EPS*w0*left)
            throw new IllegalArgumentException("Trapezoid has zero height");
        double distanceFromPlane=Math.abs(n.dot(c.minus(a1)))/n.length();
        if (distanceFromPlane > EPS*Math.max(1,Math.max(w0,left)))
            throw new IllegalArgumentException("Trapezoid points must be coplanar");
    }
}
