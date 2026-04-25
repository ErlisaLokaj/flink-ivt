package com.flinkivt.functions;

import com.flinkivt.model.GazeEvent;
import com.flinkivt.model.IvtParams;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Preprocesses raw gaze samples before IVT classification.
 *
 * <p>Implements the following steps matching the R reference implementation:
 * eye averaging, sentinel value removal, gap filling via run-length encoding
 * and linear interpolation, and optional noise reduction.
 */
public class GazePreprocessor {

    /**
     * A single preprocessed gaze sample, ready for velocity computation.
     * Contains both raw and interpolated signal values.
     */
    public static class Sample implements Serializable {
        public String sessionId;
        public double ts;
        public int originalIndex;

        public Double rawGazeX;
        public Double rawGazeY;
        public Double rawDistance;

        public double interpolatedGazeX = Double.NaN;
        public double interpolatedGazeY = Double.NaN;
        public double interpolatedDistance = Double.NaN;

        public Double gazeVelocityAngle;
        public Double gazeAccelerationAngle;

        public Double gazeXmm;
        public Double gazeYmm;
        public Double gazeZmm;

        public Double eyeXmm;
        public Double eyeYmm;
        public Double eyeZmm;

        public double screenW;
        public double screenH;
        public double monitorInches;
        public double dpi;
    }

    private static class GapInfo {
        int start;
        int end;

        GapInfo(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    /**
     * Computes the average timestamp difference between consecutive samples.
     * Used to determine window sizes for gap filling and velocity computation.
     */
    public static Double computeAvgTimestampDiff(List<GazeEvent> events) {
        if (events == null || events.size() < 2) return null;

        int limit = Math.min(100, events.size());
        double sum = 0.0;
        int count = 0;

        for (int i = 1; i < limit; i++) {
            Double t1 = events.get(i - 1).originalTimestamp != null
                    ? events.get(i - 1).originalTimestamp
                    : (events.get(i - 1).ts != null ? events.get(i - 1).ts.doubleValue() : null);

            Double t2 = events.get(i).originalTimestamp != null
                    ? events.get(i).originalTimestamp
                    : (events.get(i).ts != null ? events.get(i).ts.doubleValue() : null);

            if (t1 != null && t2 != null) {
                sum += Math.abs(t2 - t1);
                count++;
            }
        }

        return count == 0 ? null : sum / count;
    }

    /**
     * Processes a window of raw gaze events into preprocessed samples.
     * Performs eye averaging, sentinel value removal, gap filling, and
     * optional noise reduction — matching the R reference implementation.
     */
    public static List<Sample> process(List<GazeEvent> events, IvtParams params,
                                       double diffTimestamps) {
        List<Sample> samples = new ArrayList<>();
        if (events == null || events.isEmpty()) return samples;

        for (int idx = 0; idx < events.size(); idx++) {
            GazeEvent e = events.get(idx);
            if (e.gazeLeftX != null && e.gazeLeftX == -1.0) e.gazeLeftX = null;
            if (e.gazeLeftY != null && e.gazeLeftY == -1.0) e.gazeLeftY = null;
            if (e.gazeRightX != null && e.gazeRightX == -1.0) e.gazeRightX = null;
            if (e.gazeRightY != null && e.gazeRightY == -1.0) e.gazeRightY = null;
            if (e.distLeft != null && e.distLeft == -1.0) e.distLeft = null;
            if (e.distRight != null && e.distRight == -1.0) e.distRight = null;

            Sample s = new Sample();
            s.sessionId = e.sessionId;
            s.originalIndex = idx;
            s.ts = e.originalTimestamp != null ? e.originalTimestamp
                    : (e.ts != null ? e.ts.doubleValue() : 0.0);

            s.rawGazeX    = avgOrNull(e.gazeLeftX, e.gazeRightX);
            s.rawGazeY    = avgOrNull(e.gazeLeftY, e.gazeRightY);
            s.rawDistance = avgOrNull(e.distLeft, e.distRight);

            s.screenW       = e.screenW != null ? e.screenW : params.screenResolutionWidth;
            s.screenH       = e.screenH != null ? e.screenH : params.screenResolutionHeight;
            s.monitorInches = e.monitorInches != null ? e.monitorInches : params.monitorSize;

            s.dpi = Math.sqrt(s.screenW * s.screenW + s.screenH * s.screenH)
                    / Math.max(1.0, s.monitorInches);

            s.interpolatedGazeX    = s.rawGazeX != null ? s.rawGazeX : Double.NaN;
            s.interpolatedGazeY    = s.rawGazeY != null ? s.rawGazeY : Double.NaN;
            s.interpolatedDistance = s.rawDistance != null ? s.rawDistance : Double.NaN;

            samples.add(s);
        }

        if (params.gapFill) {
            computeGap(samples, diffTimestamps, "gazeX", params.maxGapLength);
            computeGap(samples, diffTimestamps, "gazeY", params.maxGapLength);
            computeGap(samples, diffTimestamps, "distance", params.maxGapLength);
        }

        if (params.noiseReduction) {
            applyMovingFilter(samples, params.windowNoise, params.filterType, "gazeX");
            applyMovingFilter(samples, params.windowNoise, params.filterType, "gazeY");
        }

        return samples;
    }

    private static void computeGap(List<Sample> samples, double diffTimestamps,
                                   String signal, long maxGapLength) {
        List<Integer> quality = new ArrayList<>();

        for (Sample s : samples) {
            Double val = getRawSignalValue(s, signal);
            int q;
            if ("distance".equals(signal)) {
                q = (val != null && val != 0.0) ? 1 : 0;
            } else {
                q = (val != null) ? 1 : 0;
            }
            quality.add(q);
        }

        List<GapInfo> gaps = detectGapsWithRLE(quality, diffTimestamps,
                maxGapLength, samples.size());

        if (!gaps.isEmpty()) {
            interpolateSignal(samples, gaps, signal);
        } else {
            for (Sample s : samples) {
                Double raw = getRawSignalValue(s, signal);
                setInterpolatedSignalValue(s, signal, raw != null ? raw : Double.NaN);
            }
        }
    }

    private static List<GapInfo> detectGapsWithRLE(List<Integer> quality,
                                                   double diffTimestamps,
                                                   long maxGapLength,
                                                   int totalSize) {
        List<GapInfo> gaps = new ArrayList<>();
        if (quality.isEmpty()) return gaps;

        int currentValue = quality.get(0);
        int runStart = 0;
        int runLength = 1;

        for (int i = 1; i < quality.size(); i++) {
            if (quality.get(i).equals(currentValue)) {
                runLength++;
            } else {
                maybeAddGap(gaps, currentValue, runStart, runLength,
                        diffTimestamps, maxGapLength, totalSize);
                currentValue = quality.get(i);
                runStart = i;
                runLength = 1;
            }
        }

        maybeAddGap(gaps, currentValue, runStart, runLength,
                diffTimestamps, maxGapLength, totalSize);
        return gaps;
    }

    private static void maybeAddGap(List<GapInfo> gaps, int currentValue,
                                    int runStart, int runLength,
                                    double diffTimestamps, long maxGapLength,
                                    int totalSize) {
        if (currentValue != 0) return;

        boolean shortEnough  = runLength + 1 <= (maxGapLength / diffTimestamps);
        boolean notAtStart   = runStart != 0;
        boolean notAtEnd     = (runStart + runLength) != totalSize;

        if (shortEnough && notAtStart && notAtEnd) {
            gaps.add(new GapInfo(runStart, runStart + runLength - 1));
        }
    }

    private static void interpolateSignal(List<Sample> samples, List<GapInfo> gaps,
                                          String signal) {
        for (GapInfo gap : gaps) {
            Double before = getInterpolatedSignalValue(samples.get(gap.start - 1), signal);
            Double after  = getInterpolatedSignalValue(samples.get(gap.end + 1), signal);
            if (before == null) before = getRawSignalValue(samples.get(gap.start - 1), signal);
            if (after  == null) after  = getRawSignalValue(samples.get(gap.end + 1), signal);

            if (before == null || after == null) continue;

            int gapDuration = gap.end - gap.start + 1;
            for (int i = 0; i < gapDuration; i++) {
                double ratio = (double) (i + 1) / (gapDuration + 1);
                double interpolated = before + ratio * (after - before);
                setInterpolatedSignalValue(samples.get(gap.start + i), signal, interpolated);
            }
        }

        for (Sample s : samples) {
            if (getInterpolatedSignalValue(s, signal) == null) {
                Double raw = getRawSignalValue(s, signal);
                setInterpolatedSignalValue(s, signal, raw != null ? raw : Double.NaN);
            }
        }
    }

    private static void applyMovingFilter(List<Sample> samples, int windowNoise,
                                          String filterType, String signal) {
        if (samples.size() < windowNoise) return;

        int halfWindow = (windowNoise - 1) / 2;
        List<Double> filtered = new ArrayList<>(Collections.nCopies(samples.size(), Double.NaN));

        for (int i = 0; i < samples.size(); i++) {
            List<Double> windowValues = new ArrayList<>();

            Double center = getInterpolatedSignalValue(samples.get(i), signal);
            if (center == null || Double.isNaN(center)) continue;
            windowValues.add(center);

            boolean validWindow = true;
            for (int offset = 1; offset <= halfWindow; offset++) {
                Double left  = (i - offset >= 0)
                        ? getInterpolatedSignalValue(samples.get(i - offset), signal) : null;
                Double right = (i + offset < samples.size())
                        ? getInterpolatedSignalValue(samples.get(i + offset), signal) : null;

                int nbSample =
                        ((left  != null && !Double.isNaN(left))  ? 1 : 0) +
                                ((right != null && !Double.isNaN(right)) ? 1 : 0);

                if (nbSample % 2 != 0) { validWindow = false; break; }

                if (left  != null && !Double.isNaN(left))  windowValues.add(left);
                if (right != null && !Double.isNaN(right)) windowValues.add(right);
            }

            if (validWindow && windowValues.size() == windowNoise) {
                if ("Average".equalsIgnoreCase(filterType)) {
                    filtered.set(i, average(windowValues));
                } else if ("Median".equalsIgnoreCase(filterType)) {
                    filtered.set(i, median(windowValues));
                }
            }
        }

        for (int i = 0; i < samples.size(); i++) {
            Double v = filtered.get(i);
            if (v != null && !Double.isNaN(v)) {
                if ("gazeX".equals(signal)) samples.get(i).interpolatedGazeX = v;
                else if ("gazeY".equals(signal)) samples.get(i).interpolatedGazeY = v;
            }
        }
    }

    private static Double avgOrNull(Double a, Double b) {
        if (a == null && b == null) return null;
        if (a == null) return b;
        if (b == null) return a;
        return (a + b) / 2.0;
    }

    private static Double getRawSignalValue(Sample s, String signal) {
        switch (signal) {
            case "gazeX":    return s.rawGazeX;
            case "gazeY":    return s.rawGazeY;
            case "distance": return s.rawDistance;
            default:         return null;
        }
    }

    private static Double getInterpolatedSignalValue(Sample s, String signal) {
        switch (signal) {
            case "gazeX":    return Double.isNaN(s.interpolatedGazeX)    ? null : s.interpolatedGazeX;
            case "gazeY":    return Double.isNaN(s.interpolatedGazeY)    ? null : s.interpolatedGazeY;
            case "distance": return Double.isNaN(s.interpolatedDistance) ? null : s.interpolatedDistance;
            default:         return null;
        }
    }

    private static void setInterpolatedSignalValue(Sample s, String signal, double value) {
        switch (signal) {
            case "gazeX":    s.interpolatedGazeX    = value; break;
            case "gazeY":    s.interpolatedGazeY    = value; break;
            case "distance": s.interpolatedDistance = value; break;
        }
    }

    private static double average(List<Double> values) {
        double sum = 0.0;
        for (double v : values) sum += v;
        return sum / values.size();
    }

    private static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int size = sorted.size();
        if (size % 2 == 0) {
            return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
        }
        return sorted.get(size / 2);
    }
}