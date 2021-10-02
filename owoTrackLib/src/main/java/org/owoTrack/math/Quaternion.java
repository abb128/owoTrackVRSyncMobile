package org.owoTrack.math;


import static java.lang.Math.cos;
import static java.lang.Math.sin;

/**
 * Quaternions are data structures built from unicorn horns.
 *
 * I nabbed this implementation from The Internet.
 */
public final class Quaternion {
    private double x;
    private double y;
    private double z;
    private double w;
    //private float[] matrixs;

    public Quaternion(final Quaternion q) {
        this(q.x, q.y, q.z, q.w);
    }

    public Quaternion(double x, double y, double z, double w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    public void set(final Quaternion q) {
        //matrixs = null;
        this.x = q.x;
        this.y = q.y;
        this.z = q.z;
        this.w = q.w;
    }

    public Quaternion(Vector3 axis, double angle) {
        set(axis, angle);
    }

    public double norm() {
        return Math.sqrt(dot(this));
    }

    public double getW() {
        return w;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    /**
     * @param axis
     *            rotation axis, unit vector
     * @param angle
     *            the rotation angle
     * @return this
     */
    public Quaternion set(Vector3 axis, double angle) {
        //matrixs = null;
        double s = (double) sin(angle / 2);
        w = (double) cos(angle / 2);
        x = axis.getX() * s;
        y = axis.getY() * s;
        z = axis.getZ() * s;
        return this;
    }

    public Quaternion mulThis(Quaternion q) {
        //matrixs = null;
        double nw = w * q.w - x * q.x - y * q.y - z * q.z;
        double nx = w * q.x + x * q.w + y * q.z - z * q.y;
        double ny = w * q.y + y * q.w + z * q.x - x * q.z;
        z = w * q.z + z * q.w + x * q.y - y * q.x;
        w = nw;
        x = nx;
        y = ny;
        return this;
    }

    public Quaternion scaleThis(double scale) {
        if (scale != 1) {
            //matrixs = null;
            w *= scale;
            x *= scale;
            y *= scale;
            z *= scale;
        }
        return this;
    }

    public Quaternion divThis(double scale) {
        if (scale != 1) {
            //matrixs = null;
            w /= scale;
            x /= scale;
            y /= scale;
            z /= scale;
        }
        return this;
    }

    public double dot(Quaternion q) {
        return x * q.x + y * q.y + z * q.z + w * q.w;
    }

    public boolean equals(Quaternion q) {
        return x == q.x && y == q.y && z == q.z && w == q.w;
    }

    public Quaternion interpolateThis(Quaternion q, double t) {
        if (!equals(q)) {
            double d = dot(q);
            double qx, qy, qz, qw;

            if (d < 0f) {
                qx = -q.x;
                qy = -q.y;
                qz = -q.z;
                qw = -q.w;
                d = -d;
            } else {
                qx = q.x;
                qy = q.y;
                qz = q.z;
                qw = q.w;
            }

            double f0, f1;

            if ((1 - d) > 0.1f) {
                double angle = (double) Math.acos(d);
                double s = (double) sin(angle);
                double tAngle = t * angle;
                f0 = (double) sin(angle - tAngle) / s;
                f1 = (double) sin(tAngle) / s;
            } else {
                f0 = 1 - t;
                f1 = t;
            }

            x = f0 * x + f1 * qx;
            y = f0 * y + f1 * qy;
            z = f0 * z + f1 * qz;
            w = f0 * w + f1 * qw;
        }

        return this;
    }

    public Quaternion normalizeThis() {
        return divThis(norm());
    }

    public Quaternion interpolate(Quaternion q, double t) {
        return new Quaternion(this).interpolateThis(q, t);
    }

    /**
     * Converts this Quaternion into a matrix, returning it as a float array.
     */
    public float[] toMatrix() {
        float[] matrixs = new float[16];
        toMatrix(matrixs);
        return matrixs;
    }

    /**
     * Converts this Quaternion into a matrix, placing the values into the given array.
     * @param matrixs 16-length float array.
     */
    public final void toMatrix(float[] matrixs) {
        matrixs[3] = 0.0f;
        matrixs[7] = 0.0f;
        matrixs[11] = 0.0f;
        matrixs[12] = 0.0f;
        matrixs[13] = 0.0f;
        matrixs[14] = 0.0f;
        matrixs[15] = 1.0f;

        matrixs[0] = (float) (1.0f - (2.0f * ((y * y) + (z * z))));
        matrixs[1] = (float) (2.0f * ((x * y) - (z * w)));
        matrixs[2] = (float) (2.0f * ((x * z) + (y * w)));

        matrixs[4] = (float) (2.0f * ((x * y) + (z * w)));
        matrixs[5] = (float) (1.0f - (2.0f * ((x * x) + (z * z))));
        matrixs[6] = (float) (2.0f * ((y * z) - (x * w)));

        matrixs[8] = (float) (2.0f * ((x * z) - (y * w)));
        matrixs[9] = (float) (2.0f * ((y * z) + (x * w)));
        matrixs[10] = (float) (1.0f - (2.0f * ((x * x) + (y * y))));
    }


    public static Quaternion create_from_axis_angle(float xx, float yy, float zz, float a){
        // Here we calculate the sin( theta / 2) once for optimization
        float factor = (float) sin( a / 2.0 );

        // Calculate the x, y and z of the quaternion
        float x = xx * factor;
        float y = yy * factor;
        float z = zz * factor;

        // Calcualte the w value by cos( theta / 2 )
        float w = (float) cos( a / 2.0 );

        Quaternion quat = new Quaternion(x, y, z, w);
        quat.normalizeThis();
        return quat;
    }


    public float yaw(){
        return (float) Math.atan2(2.0f * (w * z + x * y), w * w + x * x - y * y - z * z);
    }
}