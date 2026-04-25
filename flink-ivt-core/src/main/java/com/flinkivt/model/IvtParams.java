package com.flinkivt.model;

import java.io.Serializable;

/**
 * Configuration parameters for the IVT fixation and saccade detection algorithm.
 *
 * <p>Default values match the validated R reference implementation.
 * Use the builder-style setter methods to customize for your setup.
 *
 * <p>Example:
 * <pre>
 *   IvtParams params = new IvtParams()
 *       .withScreenResolution(1920, 1080)
 *       .withMonitorSize(24.0)
 *       .withVelocityThreshold(30.0)
 *       .withMinFixationDuration(60)
 *       .withMergeFixation(true, 75, 0.5);
 * </pre>
 */
public class IvtParams implements Serializable {

    /** Screen resolution in pixels. */
    public int screenResolutionWidth = 1920;
    public int screenResolutionHeight = 1080;

    /** Physical monitor size in inches. */
    public double monitorSize = 24.0;

    /** Gap filling: interpolate over short missing data runs. */
    public boolean gapFill = true;
    public long maxGapLength = 75;

    /** Noise reduction filter applied before velocity computation. */
    public boolean noiseReduction = false;
    public int windowNoise = 5;
    public String filterType = "Average";

    /** Velocity window size in ms and threshold in deg/s for IVT classification. */
    public int windowVelocity = 20;
    public double velocityThreshold = 30.0;

    /** Minimum fixation duration in ms. Shorter fixations are discarded. */
    public long minDurationFixation = 60;
    public boolean discardShortFixation = true;

    /** Fixation merging: merge nearby fixations within time and angle bounds. */
    public boolean mergeFixation = true;
    public long maxTimeBtwFixation = 75;
    public double maxAngleBtwFixation = 0.5;

    /** Whether to detect saccades in addition to fixations. */
    public boolean detectSaccades = true;

    public IvtParams() {}

    public IvtParams withScreenResolution(int width, int height) {
        this.screenResolutionWidth = width;
        this.screenResolutionHeight = height;
        return this;
    }

    public IvtParams withMonitorSize(double inches) {
        this.monitorSize = inches;
        return this;
    }

    public IvtParams withGapFill(boolean enabled, long maxGapMs) {
        this.gapFill = enabled;
        this.maxGapLength = maxGapMs;
        return this;
    }

    public IvtParams withNoiseReduction(boolean enabled, int windowSize, String filterType) {
        this.noiseReduction = enabled;
        this.windowNoise = windowSize;
        this.filterType = filterType;
        return this;
    }

    public IvtParams withVelocityThreshold(double threshold) {
        this.velocityThreshold = threshold;
        return this;
    }

    public IvtParams withWindowVelocity(int windowMs) {
        this.windowVelocity = windowMs;
        return this;
    }

    public IvtParams withMinFixationDuration(long durationMs) {
        this.minDurationFixation = durationMs;
        return this;
    }

    public IvtParams withDiscardShortFixation(boolean discard) {
        this.discardShortFixation = discard;
        return this;
    }

    public IvtParams withMergeFixation(boolean merge, long maxTimeBetween,
                                       double maxAngleBetween) {
        this.mergeFixation = merge;
        this.maxTimeBtwFixation = maxTimeBetween;
        this.maxAngleBtwFixation = maxAngleBetween;
        return this;
    }

    public IvtParams withDetectSaccades(boolean detect) {
        this.detectSaccades = detect;
        return this;
    }
}