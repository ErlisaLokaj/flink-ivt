package com.flinkivt.model;

import java.io.Serializable;

/**
 * A detected saccade event produced by the IVT pipeline.
 * Contains temporal, kinematic, and directional properties.
 */
public class SaccadeEvent implements Serializable {

    /** Unique saccade identifier within the session. */
    public double saccadeID;

    /** Saccade start, end, and duration in milliseconds. */
    public double saccadeStart;
    public double saccadeEnd;
    public double saccadeDuration;

    /** Saccade amplitude in degrees of visual angle. NaN if undetermined. */
    public double amplitude;

    /** Peak velocity, acceleration, and deceleration in deg/s and deg/s². */
    public double peakVelocity;
    public double peakAcceleration;
    public double peakDeceleration;

    /** Saccade direction angle in degrees (0–360). NaN if undetermined. */
    public double dirAngle;

    public SaccadeEvent() {}

    @Override
    public String toString() {
        return String.format(
                "SaccadeEvent{id=%.0f, dur=%.3fms, amp=%.3f, dir=%.1f}",
                saccadeID, saccadeDuration, amplitude, dirAngle
        );
    }
}