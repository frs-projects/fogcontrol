package net.frsprojects.fogcontrol.client;

import net.frsprojects.fogcontrol.FogSettings;
import net.frsprojects.fogcontrol.core.FogState;

/**
 * Client-side state.
 * Deliberately contains no client-only or Distant Horizons imports so it can be referenced from anywhere.
 */
public final class ClientFogState {
    private ClientFogState() {}

    /** One tick = 1/20 s. Transitions run on real time, so they stay smooth even if the server lags. */
    private static final long NANOS_PER_TICK = 50_000_000L;

    /** Settings received from the server. null = server doesn't run Fog Control, fog stays vanilla. */
    private static volatile FogSettings current = null;

    // Transition from 'from' to 'to', started at startNanos, lasting durationNanos (0 = done).
    private static FogState from = null;
    private static FogState to = null;
    private static long startNanos = 0L;
    private static long durationNanos = 0L;

    /**
     * Render distance (blocks) at which our terrain fog override was applied this frame,
     * or NaN if vanilla fog was left alone (water, blindness, Nether, no server support...).
     * Used to line up Distant Horizons' fog with ours.
     */
    private static volatile float appliedRenderDistance = Float.NaN;

    /** Optional hook (used by the Distant Horizons compat) run when settings arrive or are cleared. */
    private static Runnable changeListener = null;

    /** Latest settings from the server (the transition target), or null if the server doesn't run Fog Control. */
    public static FogSettings get() {
        return current;
    }

    /** The values to show right now, including any transition in progress. null = vanilla. */
    public static FogState effective() {
        FogState target = to;
        if (current == null || target == null) return null;
        if (durationNanos <= 0L) return target;

        long elapsed = System.nanoTime() - startNanos;
        if (elapsed >= durationNanos) {
            durationNanos = 0L; // finished
            return target;
        }
        float t = (float) elapsed / (float) durationNanos;
        t = t * t * (3.0F - 2.0F * t); // ease in and out
        return FogState.lerp(from, target, t);
    }

    public static void set(FogSettings settings) {
        FogState newTarget = FogState.of(settings.minPercent(), settings.maxPercent(),
                settings.customColorEnabled(), settings.customColor(),
                settings.skyColorEnabled(), settings.skyColor());
        FogState now = effective();

        if (now == null || settings.transitionTicks() <= 0) {
            // First settings after joining (or transitions disabled): apply instantly.
            from = newTarget;
            durationNanos = 0L;
        } else {
            // Start from whatever is on screen right now, even mid-transition.
            from = now.startingPointFor(newTarget);
            durationNanos = settings.transitionTicks() * NANOS_PER_TICK;
            startNanos = System.nanoTime();
        }
        to = newTarget;
        current = settings;
        notifyListener();
    }

    public static void clear() {
        current = null;
        from = null;
        to = null;
        durationNanos = 0L;
        appliedRenderDistance = Float.NaN;
        notifyListener();
    }

    public static float getAppliedRenderDistance() {
        return appliedRenderDistance;
    }

    public static void setAppliedRenderDistance(float renderDistance) {
        appliedRenderDistance = renderDistance;
    }

    public static void setChangeListener(Runnable listener) {
        changeListener = listener;
    }

    private static void notifyListener() {
        Runnable l = changeListener;
        if (l != null) l.run();
    }
}
