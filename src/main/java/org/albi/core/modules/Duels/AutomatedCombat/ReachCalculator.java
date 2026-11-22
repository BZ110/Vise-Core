package org.albi.core.modules.Duels.AutomatedCombat;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public final class ReachCalculator {

    private static final double BASE_MARGIN = 0.0005D;
    private static final double EXTRA_MARGIN = 0.03D;
    private static final double DEFAULT_REACH = 3.0D;
    private static final double RAY_EXTRA_DISTANCE = 3.0D;
    private static final double EPSILON = 1.0E-9D;

    private ReachCalculator() {
    }

    public static ReachResult measure(Player attacker, Player victim) {
        double allowedReach = getAllowedReach(attacker);

        BoundingBox box = victim.getBoundingBox().clone().expand(BASE_MARGIN + EXTRA_MARGIN);

        Location eyeLocation = attacker.getEyeLocation();
        Vector eye = eyeLocation.toVector();
        Vector look = eyeLocation.getDirection().normalize();

        if (isInside(eye, box)) {
            return new ReachResult(0.0D, allowedReach, false);
        }

        Vector intercept = rayBoxIntercept(eye, look, allowedReach + RAY_EXTRA_DISTANCE, box);
        if (intercept != null) {
            double distance = eye.distance(intercept);
            return new ReachResult(distance, allowedReach, false);
        }

        double nearestDistance = distanceToBox(eye, box);
        return new ReachResult(nearestDistance, allowedReach, true);
    }

    private static double getAllowedReach(Player attacker) {
        if (attacker.getAttribute(Attribute.ENTITY_INTERACTION_RANGE) != null) {
            return attacker.getAttribute(Attribute.ENTITY_INTERACTION_RANGE).getValue();
        }
        return DEFAULT_REACH;
    }

    private static boolean isInside(Vector point, BoundingBox box) {
        return point.getX() >= box.getMinX() && point.getX() <= box.getMaxX()
                && point.getY() >= box.getMinY() && point.getY() <= box.getMaxY()
                && point.getZ() >= box.getMinZ() && point.getZ() <= box.getMaxZ();
    }

    private static double distanceToBox(Vector point, BoundingBox box) {
        double dx = axisDistance(point.getX(), box.getMinX(), box.getMaxX());
        double dy = axisDistance(point.getY(), box.getMinY(), box.getMaxY());
        double dz = axisDistance(point.getZ(), box.getMinZ(), box.getMaxZ());
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double axisDistance(double value, double min, double max) {
        if (value < min)
            return min - value;
        if (value > max)
            return value - max;
        return 0.0D;
    }

    private static Vector rayBoxIntercept(Vector origin, Vector direction, double maxDistance, BoundingBox box) {
        double tMin = 0.0D;
        double tMax = maxDistance;

        double[] originValues = { origin.getX(), origin.getY(), origin.getZ() };
        double[] directionValues = { direction.getX(), direction.getY(), direction.getZ() };
        double[] minValues = { box.getMinX(), box.getMinY(), box.getMinZ() };
        double[] maxValues = { box.getMaxX(), box.getMaxY(), box.getMaxZ() };

        for (int i = 0; i < 3; i++) {
            double o = originValues[i];
            double d = directionValues[i];
            double min = minValues[i];
            double max = maxValues[i];

            if (Math.abs(d) < EPSILON) {
                if (o < min || o > max) {
                    return null;
                }
                continue;
            }

            double t1 = (min - o) / d;
            double t2 = (max - o) / d;

            if (t1 > t2) {
                double temp = t1;
                t1 = t2;
                t2 = temp;
            }

            if (t1 > tMin) {
                tMin = t1;
            }

            if (t2 < tMax) {
                tMax = t2;
            }

            if (tMax < tMin) {
                return null;
            }
        }

        if (tMin < 0.0D || tMin > maxDistance) {
            return null;
        }

        return origin.clone().add(direction.clone().multiply(tMin));
    }

    public static final class ReachResult {
        private final double distance;
        private final double allowedReach;
        private final boolean invalidHitbox;

        public ReachResult(double distance, double allowedReach, boolean invalidHitbox) {
            this.distance = distance;
            this.allowedReach = allowedReach;
            this.invalidHitbox = invalidHitbox;
        }

        public double getDistance() {
            return distance;
        }

        public double getAllowedReach() {
            return allowedReach;
        }

        public boolean isInvalidHitbox() {
            return invalidHitbox;
        }

        public boolean isReachViolation() {
            return !invalidHitbox && distance > allowedReach;
        }
    }
}