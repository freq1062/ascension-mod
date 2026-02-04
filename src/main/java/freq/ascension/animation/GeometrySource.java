package freq.ascension.animation;

import org.joml.Quaternionf;
import org.joml.Vector3f;

public class GeometrySource {
    // Returns a point on a circle in 3D space
    public static Vector3f circle(Vector3f center, Vector3f normal, float radius, boolean hollow) {
        // Robustly build an orthonormal basis around `normal`.
        Vector3f n = new Vector3f(normal);
        if (n.length() < 1e-6f) {
            n.set(0, 1, 0);
        }
        n.normalize();

        // Choose an arbitrary vector that's not parallel to n
        Vector3f arbitrary = Math.abs(n.x) < 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);

        Vector3f tangent = new Vector3f();
        n.cross(arbitrary, tangent);
        if (tangent.length() < 1e-6f) {
            arbitrary.set(0, 0, 1);
            n.cross(arbitrary, tangent);
        }
        tangent.normalize();

        Vector3f bitangent = new Vector3f();
        n.cross(tangent, bitangent).normalize();

        float r = hollow ? radius : (float) (radius * Math.sqrt(Math.random()));
        float theta = (float) (Math.random() * 2 * Math.PI);

        Vector3f offset = new Vector3f(tangent).mul((float) (r * Math.cos(theta)))
                .add(new Vector3f(bitangent).mul((float) (r * Math.sin(theta))));

        return new Vector3f(center).add(offset);
    }

    // Uniform random rotation
    public static Quaternionf randomRot() {
        return new Quaternionf().rotateXYZ((float) (Math.random() * Math.PI * 2),
                (float) (Math.random() * Math.PI * 2), (float) (Math.random() * Math.PI * 2));
    }

    public static Quaternionf faceVector(Vector3f vec) {
        Vector3f v = new Vector3f(vec);
        if (v.lengthSquared() < 1e-8f) {
            return new Quaternionf(); // identity
        }
        v.normalize();
        Vector3f forward = new Vector3f(0, 0, 1);
        return new Quaternionf().rotationTo(forward, v);
    }

    // Uniform random scale, still cube shaped
    public static Vector3f randomScaleEqual(float low, float high) {
        if (low > high) {
            float t = low;
            low = high;
            high = t;
        }
        float range = high - low;
        return new Vector3f((float) Math.random() * range);
    }
}