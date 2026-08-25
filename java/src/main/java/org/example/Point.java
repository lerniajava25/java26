package org.example;

/**
 * Represents a point in a 2D Cartesian coordinate system with immutable x and y coordinates.
 */
public class Point {
    private final double x;
    private final double y;

    /**
     * Creates a Point object at the origin of the 2D Cartesian coordinate system (0.0, 0.0).
     * Both the x and y coordinates are initialized to 0.0.
     */
    public Point() {
        this.x = 0.0;
        this.y = 0.0;
    }

    /**
     * Creates a Point object with the specified x and y coordinates in the
     * 2D Cartesian coordinate system.
     *
     * @param x The x-coordinate of the point.
     * @param y The y-coordinate of the point.
     */
    public Point(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Creates a new Point object by copying the x and y coordinates from another Point.
     *
     * @param point The Point object whose coordinates are to be copied.
     */
    public Point(Point point) {
        this.x = point.x;
        this.y = point.y;
    }

    public static Point of(double x, double y) {
        return new Point(x, y);
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof Point point)) return false;
        return Double.compare(x, point.x) == 0 && Double.compare(y, point.y) == 0;
    }

    @Override
    public int hashCode() {
        int result = Double.hashCode(x);
        result = 31 * result + Double.hashCode(y);
        return result;
    }
}
