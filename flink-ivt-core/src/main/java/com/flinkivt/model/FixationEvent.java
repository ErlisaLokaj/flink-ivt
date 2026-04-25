package com.flinkivt.model;

import java.io.Serializable;

/**
 * A detected fixation event produced by the IVT pipeline.
 * Contains spatial, temporal, and incoming saccade properties.
 */
public class FixationEvent implements Serializable {

    /** Unique fixation identifier within the session. */
    public double fixID;

    /** Fixation start, end, and duration in milliseconds. */
    public double fixationStart;
    public double fixationEnd;
    public double fixationDuration;

    /** Fixation centroid in screen pixels. */
    public double fixationX;
    public double fixationY;

    /** Spatial dispersion of gaze samples within the fixation in degrees. */
    public double dispersion = 0.0;

    /** Whether this fixation exceeds the minimum duration threshold. */
    public int isLongFixation = 0;

    /** Properties of the saccade immediately preceding this fixation. */
    public double incomingSaccDuration = 0.0;
    public double incomingSaccAmplitude = 0.0;
    public double incomingSaccAngle = 0.0;
    public int isIncomingRegressionAngle = 0;

    public FixationEvent() {}

    @Override
    public String toString() {
        return String.format(
                "FixationEvent{fixID=%.0f, start=%.3f, dur=%.3fms, pos=(%.1f,%.1f)}",
                fixID, fixationStart, fixationDuration, fixationX, fixationY
        );
    }
}