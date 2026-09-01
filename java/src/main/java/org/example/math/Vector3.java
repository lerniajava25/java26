package org.example.math;

public record Vector3(double x, double y, double z) {

    //SIMD - Single Instruction Multiple Data
    //Register1 -> a.x  a.y  a.z  1
    //Register2 -> b.x  b.y  b.z  1


    public double dotProduct(Vector3 b) {
        return this.x * b.x + this.y * b.y + this.z * b.z;
    }

    public static double dotProduct(Vector3 a, Vector3 b) {
        return a.x * b.x + a.y * b.y + a.z * b.z;
    }

    public Vector3 crossProduct(Vector3 b) {
        return new Vector3(this.y * b.z - this.z * b.y, this.z * b.x - this.x * b.z, this.x * b.y - this.y * b.x);
    }

    public static Vector3 crossProduct(Vector3 a, Vector3 b) {
        return new Vector3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x);
    }
}
