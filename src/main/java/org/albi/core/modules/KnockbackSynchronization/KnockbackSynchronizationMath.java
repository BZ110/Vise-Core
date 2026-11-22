package org.albi.core.modules.KnockbackSynchronization;

public final class KnockbackSynchronizationMath {

    private static final double TERMINAL_VELOCITY = 3.92D;
    private static final double VELOCITY_MULTIPLIER = 0.98D;
    private static final int MAX_TICKS = 30;

    private KnockbackSynchronizationMath() {
    }

    public static double calculateDistanceTraveled(double velocity, int ticks, double gravity) {
        double distance = 0.0D;
        for (int i = 0; i < ticks; i++) {
            distance += velocity;
            velocity = Math.min((velocity - gravity) * VELOCITY_MULTIPLIER, TERMINAL_VELOCITY);
        }
        return distance;
    }

    public static int calculateFallTime(double initialVelocity, double distance, double gravity) {
        double velocity = Math.abs(initialVelocity);
        int ticks = 0;
        while (distance > 0.0D) {
            if (ticks > MAX_TICKS) {
                return -1;
            }
            velocity += gravity;
            velocity = Math.min(velocity, TERMINAL_VELOCITY);
            velocity *= VELOCITY_MULTIPLIER;
            distance -= velocity;
            ticks++;
        }
        return ticks;
    }

    public static int calculateTimeToMaxVelocity(double velocity, double gravity) {
        int ticks = 0;
        while (velocity > 0.0D) {
            if (ticks > MAX_TICKS) {
                return -1;
            }
            velocity -= gravity;
            velocity = Math.min(velocity, TERMINAL_VELOCITY);
            velocity *= VELOCITY_MULTIPLIER;
            ticks++;
        }
        return ticks;
    }

    /**
     * Simulates N ticks of gravity to predict what Y velocity the CLIENT
     * would have at the current moment. Used for off-ground sync to
     * compensate for the ping-induced gravity desync.
     */
    public static double getCompensatedVerticalVelocity(double currentY, double gravity, int ticks) {
        double y = currentY;
        for (int i = 0; i < ticks; i++) {
            y = (y - gravity) * VELOCITY_MULTIPLIER;
        }
        return y;
    }
}
