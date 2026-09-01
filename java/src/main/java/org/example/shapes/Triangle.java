package org.example.shapes;

import org.example.math.Ray;
import org.example.math.Vector3;

public class Triangle implements Shape {

    private final Vector3 p1;
    private final Vector3 p2;
    private final Vector3 p3;

    public Triangle(Vector3 p1, Vector3 p2, Vector3 p3) {
        this.p1 = p1;
        this.p2 = p2;
        this.p3 = p3;
    }

    @Override
    public Hit hit(Ray ray) {
        return new Hit(false);
    }
}
