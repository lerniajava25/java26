package org.example.shapes;

import org.example.math.Ray;
import org.example.math.Vector3;

public class Sphere implements Shape {

    private final Vector3 center;
    private final double radius;

    public Sphere(Vector3 center, double radius) {
        this.center = center;
        this.radius = radius;
    }

    @Override
    public Hit hit(Ray ray) {
        //Implement collision check
        return new Hit(false);
    }
}
