package com.flinkivt.functions;

import com.flinkivt.model.FixationEvent;
import com.flinkivt.model.IVTResult;
import com.flinkivt.model.IvtParams;
import com.flinkivt.model.SaccadeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Java translation of the R IVT algorithm (iMotions reference implementation).
 *
 * <p>Implements the complete IVT detection pipeline:
 * velocity computation, event classification, fixation detection,
 * fixation merging, fixation discarding, and saccade detection.
 *
 * <p>All functions are validated against the R reference implementation
 * with 100% event-level correspondence.
 */
public class IVTProcessor {

    /**
     * Internal representation of a fixation during processing.
     * Not exposed in the public API — converted to {@link FixationEvent} on output.
     */
    private static class ProcessingFixation {
        int    id;
        double startTime;
        double endTime;
        double duration;

        double centroidX;
        double centroidY;
        double centroidXmm;
        double centroidYmm;
        double centroidZmm;

        double eyeXmm;
        double eyeYmm;
        double firstEyeZmm;
        double lastEyeZmm;

        Double dispersion;
        double percentageNonNA;

        int dataStart;
        int dataEnd;
    }

    /**
     * Runs the full IVT detection pipeline on a preprocessed window of gaze samples.
     *
     * @param data            preprocessed samples from {@link GazePreprocessor}
     * @param params          IVT configuration parameters
     * @param diffTimestamps  average timestamp difference between samples in ms
     * @return detected fixations and saccades for this window
     */
    public static IVTResult detect(List<GazePreprocessor.Sample> data,
                                   IvtParams params,
                                   double diffTimestamps) {
        List<FixationEvent> result = new ArrayList<>();
        List<SaccadeEvent> saccades = new ArrayList<>();

        if (data == null || data.isEmpty()) return new IVTResult(result, saccades);

        double dpi = Math.sqrt(
                (double) params.screenResolutionWidth  * params.screenResolutionWidth +
                        (double) params.screenResolutionHeight * params.screenResolutionHeight
        ) / params.monitorSize;

        velocityComputation(data, diffTimestamps,
                params.screenResolutionWidth, params.screenResolutionHeight,
                dpi, params.windowVelocity);

        List<Integer> eventType = classifyEventTypes(data, params.velocityThreshold);

        if (params.detectSaccades) {
            saccades = ivtSaccadeProcessing(data, eventType, dpi);
        }

        List<ProcessingFixation> fixations = ivtFixationProcessing(data, eventType, dpi);

        if (params.mergeFixation && fixations.size() >= 2) {
            fixations = mergeFixations(fixations, data, eventType,
                    params.maxTimeBtwFixation, params.maxAngleBtwFixation, dpi);
        }

        if (params.discardShortFixation && !fixations.isEmpty()) {
            fixations = discardFixations(fixations, params.minDurationFixation);
        }

        for (ProcessingFixation f : fixations) {
            FixationEvent evt = new FixationEvent();
            evt.fixID            = f.id;
            evt.fixationStart    = f.startTime;
            evt.fixationEnd      = f.endTime;
            evt.fixationDuration = f.duration;
            evt.fixationX        = f.centroidX;
            evt.fixationY        = f.centroidY;
            evt.dispersion       = f.dispersion != null ? f.dispersion : 0.0;
            evt.isLongFixation   = f.duration >= 250 ? 1 : 0;
            result.add(evt);
        }

        return new IVTResult(result, saccades);
    }

    private static void velocityComputation(List<GazePreprocessor.Sample> data,
                                            double diffTimestamps,
                                            int screenResolutionWidth,
                                            int screenResolutionHeight,
                                            double dpi,
                                            int windowVelocity) {
        if (data.size() < 2) {
            for (GazePreprocessor.Sample s : data) s.gazeVelocityAngle = null;
            return;
        }

        for (GazePreprocessor.Sample s : data) {
            double gx = s.interpolatedGazeX;
            double gy = s.interpolatedGazeY;
            s.gazeXmm = Double.isNaN(gx) ? Double.NaN : toMm(gx, dpi);
            s.gazeYmm = Double.isNaN(gy) ? Double.NaN : toMm(gy, dpi);
            s.gazeZmm = 0.0;
            s.eyeXmm  = toMm(screenResolutionWidth  / 2.0, dpi);
            s.eyeYmm  = toMm(screenResolutionHeight / 2.0, dpi);
            s.eyeZmm  = s.interpolatedDistance;
        }

        int windowSample = Math.max(
                (int) Math.floor(windowVelocity / diffTimestamps + 1)
                        - (int) Math.floor(windowVelocity / diffTimestamps % 2),
                3
        );
        windowSample = Math.min(windowSample, data.size() + (data.size() % 2) - 1);
        int halfWindow = windowSample / 2;

        for (GazePreprocessor.Sample s : data) s.gazeVelocityAngle = null;

        for (int idx = halfWindow; idx < data.size() - halfWindow; idx++) {
            int si = idx - halfWindow;
            int ei = idx + halfWindow;

            boolean bothExist = true;
            for (int j = si; j <= ei; j++) {
                if (Double.isNaN(data.get(j).gazeXmm) ||
                        Double.isNaN(data.get(j).gazeYmm)) {
                    bothExist = false;
                    break;
                }
            }
            if (!bothExist) continue;

            GazePreprocessor.Sample s0 = data.get(si);
            GazePreprocessor.Sample s1 = data.get(ei);
            GazePreprocessor.Sample sc = data.get(idx);

            double[] gaze0 = { s0.gazeXmm, s0.gazeYmm, s0.gazeZmm };
            double[] gaze1 = { s1.gazeXmm, s1.gazeYmm, s1.gazeZmm };
            double[] eye   = { sc.eyeXmm,  sc.eyeYmm,  sc.eyeZmm  };

            double angleDeg  = angleDegrees(vecSub(gaze0, eye), vecSub(gaze1, eye));
            double dtSeconds = (s1.ts - s0.ts) / 1000.0;

            if (dtSeconds == 0 || Double.isNaN(angleDeg)) continue;

            data.get(idx).gazeVelocityAngle = Math.abs(angleDeg / dtSeconds);
        }

        for (int i = 0; i < data.size(); i++) {
            if (i == 0) {
                data.get(i).gazeAccelerationAngle = null;
                continue;
            }
            Double v1 = data.get(i - 1).gazeVelocityAngle;
            Double v2 = data.get(i).gazeVelocityAngle;
            double dt = (data.get(i).ts - data.get(i - 1).ts) / 1000.0;
            if (v1 == null || v2 == null || dt == 0) {
                data.get(i).gazeAccelerationAngle = null;
            } else {
                data.get(i).gazeAccelerationAngle = (v2 - v1) / dt;
            }
        }
    }

    private static List<Integer> classifyEventTypes(List<GazePreprocessor.Sample> data,
                                                    double velocityThreshold) {
        List<Integer> et = new ArrayList<>(data.size());
        for (GazePreprocessor.Sample s : data) {
            if (s.gazeVelocityAngle == null)                   et.add(2);
            else if (s.gazeVelocityAngle >= velocityThreshold) et.add(1);
            else                                               et.add(0);
        }
        return et;
    }

    private static List<ProcessingFixation> ivtFixationProcessing(
            List<GazePreprocessor.Sample> data,
            List<Integer> eventType,
            double dpi) {
        List<int[]> runs = extractRuns(eventType, 0);
        List<ProcessingFixation> fixations = new ArrayList<>();

        for (int id = 0; id < runs.size(); id++) {
            int start = runs.get(id)[0];
            int end   = runs.get(id)[1];

            ProcessingFixation f = new ProcessingFixation();
            f.id        = id;
            f.dataStart = start;
            f.dataEnd   = end;

            double startMean = (start - 1 >= 0)
                    ? (data.get(start - 1).ts + data.get(start).ts) / 2.0
                    : data.get(start).ts;
            f.startTime = Math.min(startMean, data.get(start).ts);

            double endMean = (end + 1 < data.size())
                    ? (data.get(end).ts + data.get(end + 1).ts) / 2.0
                    : data.get(end).ts;
            f.endTime = Math.max(endMean, data.get(end).ts);

            f.duration = f.endTime - f.startTime;

            f.centroidX   = meanInterpolatedX(data, start, end);
            f.centroidY   = meanInterpolatedY(data, start, end);
            f.centroidXmm = meanGazeXmm(data, start, end);
            f.centroidYmm = meanGazeYmm(data, start, end);
            f.centroidZmm = data.get(start).gazeZmm;

            f.eyeXmm      = data.get(start).eyeXmm;
            f.eyeYmm      = data.get(start).eyeYmm;
            f.firstEyeZmm = data.get(start).eyeZmm;
            f.lastEyeZmm  = data.get(end).eyeZmm;

            int groupSize  = end - start + 1;
            int validCount = 0;
            for (int i = start; i <= end; i++) {
                double gx = data.get(i).interpolatedGazeX;
                double gy = data.get(i).interpolatedGazeY;
                if (!Double.isNaN(gx) && !Double.isNaN(gy)) validCount++;
            }
            f.percentageNonNA = (double) validCount / groupSize * 100.0;

            if (end != start) {
                double[][] xy = new double[groupSize][2];
                for (int i = start; i <= end; i++) {
                    xy[i - start][0] = data.get(i).interpolatedGazeX;
                    xy[i - start][1] = data.get(i).interpolatedGazeY;
                }
                double avgDist = meanInterpolatedDistance(data, start, end);
                f.dispersion = dispersionDegree(
                        xy, new double[]{ f.centroidX, f.centroidY }, avgDist, dpi);
            } else {
                f.dispersion = null;
            }

            fixations.add(f);
        }
        return fixations;
    }

    private static List<SaccadeEvent> ivtSaccadeProcessing(
            List<GazePreprocessor.Sample> data,
            List<Integer> eventType,
            double dpi) {
        List<int[]> runs = extractRuns(eventType, 1);
        List<SaccadeEvent> saccades = new ArrayList<>();

        for (int id = 0; id < runs.size(); id++) {
            int start = runs.get(id)[0];
            int end   = runs.get(id)[1];

            SaccadeEvent s = new SaccadeEvent();
            s.saccadeID = id;

            double startMean = (start - 1 >= 0)
                    ? (data.get(start - 1).ts + data.get(start).ts) / 2.0
                    : data.get(start).ts;
            s.saccadeStart = Math.min(startMean, data.get(start).ts);

            double endMean = (end + 1 < data.size())
                    ? (data.get(end).ts + data.get(end + 1).ts) / 2.0
                    : data.get(end).ts;
            s.saccadeEnd = Math.max(endMean, data.get(end).ts);

            s.saccadeDuration = s.saccadeEnd - s.saccadeStart;

            if (end != start) {
                double startX  = data.get(start).interpolatedGazeX;
                double startY  = data.get(start).interpolatedGazeY;
                double endX    = data.get(end).interpolatedGazeX;
                double endY    = data.get(end).interpolatedGazeY;
                double avgDist = meanInterpolatedDistance(data, start, end);
                s.amplitude = amplitudeDegree(startX, startY, endX, endY, avgDist, dpi);
                s.dirAngle  = directionAngle(startX, startY, endX, endY);
            } else {
                s.amplitude = Double.NaN;
                s.dirAngle  = Double.NaN;
            }

            double peakVel = Double.NaN;
            for (int i = start; i <= end; i++) {
                Double v = data.get(i).gazeVelocityAngle;
                if (v != null && !Double.isNaN(v)) {
                    if (Double.isNaN(peakVel) || v > peakVel) peakVel = v;
                }
            }
            s.peakVelocity = peakVel;

            double peakAcc = 0.0;
            double peakDec = 0.0;
            for (int i = start; i <= end; i++) {
                Double a = data.get(i).gazeAccelerationAngle;
                if (a != null && !Double.isNaN(a)) {
                    if (a > peakAcc) peakAcc = a;
                    if (a < peakDec) peakDec = a;
                }
            }
            s.peakAcceleration = peakAcc == 0.0 ? Double.NaN : peakAcc;
            s.peakDeceleration = peakDec == 0.0 ? Double.NaN : peakDec;

            saccades.add(s);
        }
        return saccades;
    }

    private static List<ProcessingFixation> mergeFixations(
            List<ProcessingFixation> fixations,
            List<GazePreprocessor.Sample> data,
            List<Integer> eventType,
            long maxTimeBtwFixation,
            double maxAngleBtwFixation,
            double dpi) {
        if (fixations.size() < 2) return fixations;

        List<GazePreprocessor.Sample> fixationData = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            if (eventType.get(i) == 0) fixationData.add(data.get(i));
        }

        List<int[]> fixCombn = buildFixCombn(fixations, maxTimeBtwFixation);
        if (fixCombn.isEmpty()) {
            recomputeDispersion(fixations, fixationData, dpi);
            return fixations;
        }

        boolean changed = true;
        while (changed && fixations.size() >= 2 && !fixCombn.isEmpty()) {
            changed = false;

            List<int[]> toMerge = new ArrayList<>();
            for (int[] pair : fixCombn) {
                int fi = pair[0];
                int fj = pair[1];
                if (fi >= fixations.size() || fj >= fixations.size()) continue;
                double angle = calculateAngleBetweenFixations(
                        fixations.get(fi), fixations.get(fj));
                if (!Double.isNaN(angle) && angle <= maxAngleBtwFixation) {
                    toMerge.add(new int[]{ fi, fj });
                }
            }
            if (toMerge.isEmpty()) break;

            boolean removing = true;
            while (removing) {
                removing = false;
                for (int k = 1; k < toMerge.size(); k++) {
                    if (toMerge.get(k - 1)[1] >= toMerge.get(k)[0]) {
                        toMerge.remove(k);
                        removing = true;
                        break;
                    }
                }
            }
            if (toMerge.isEmpty()) break;

            for (int k = toMerge.size() - 1; k >= 0; k--) {
                int fi = toMerge.get(k)[0];
                int fj = toMerge.get(k)[1];
                if (fi >= fixations.size() || fj >= fixations.size()) continue;

                List<ProcessingFixation> subset =
                        new ArrayList<>(fixations.subList(fi, fj + 1));
                ProcessingFixation merged = mergeAdjacentFixations(subset);
                merged.id = fixations.get(fi).id;

                for (int r = fj; r > fi; r--) fixations.remove(r);
                fixations.set(fi, merged);
            }

            fixCombn = buildFixCombn(fixations, maxTimeBtwFixation);
            changed = true;
        }

        for (int i = 0; i < fixations.size(); i++) fixations.get(i).id = i;
        recomputeDispersion(fixations, fixationData, dpi);
        return fixations;
    }

    private static List<int[]> buildFixCombn(List<ProcessingFixation> fixations,
                                             long maxTimeBtwFixation) {
        List<int[]> pairs = new ArrayList<>();
        for (int i = 0; i < fixations.size() - 1; i++) {
            for (int j = i + 1; j < fixations.size(); j++) {
                double gap = fixations.get(j).startTime - fixations.get(i).endTime;
                if (gap > maxTimeBtwFixation) break;
                pairs.add(new int[]{ i, j });
            }
        }
        return pairs;
    }

    private static ProcessingFixation mergeAdjacentFixations(
            List<ProcessingFixation> subset) {
        double totalDuration = 0.0;
        for (ProcessingFixation f : subset) totalDuration += f.duration;

        ProcessingFixation first = subset.get(0);
        ProcessingFixation last  = subset.get(subset.size() - 1);
        ProcessingFixation m     = new ProcessingFixation();

        m.id              = first.id;
        m.startTime       = first.startTime;
        m.centroidZmm     = first.centroidZmm;
        m.eyeXmm          = first.eyeXmm;
        m.eyeYmm          = first.eyeYmm;
        m.firstEyeZmm     = first.firstEyeZmm;
        m.dataStart       = first.dataStart;
        m.percentageNonNA = first.percentageNonNA;

        m.endTime    = last.endTime;
        m.lastEyeZmm = last.lastEyeZmm;
        m.dataEnd    = last.dataEnd;
        m.duration   = m.endTime - m.startTime;

        double cx = 0, cy = 0, cxmm = 0, cymm = 0;
        for (ProcessingFixation f : subset) {
            double w = f.duration / totalDuration;
            cx   += f.centroidX   * w;
            cy   += f.centroidY   * w;
            cxmm += f.centroidXmm * w;
            cymm += f.centroidYmm * w;
        }
        m.centroidX   = cx;
        m.centroidY   = cy;
        m.centroidXmm = cxmm;
        m.centroidYmm = cymm;
        m.dispersion  = null;

        return m;
    }

    private static void recomputeDispersion(List<ProcessingFixation> fixations,
                                            List<GazePreprocessor.Sample> fixationData,
                                            double dpi) {
        if (fixations.isEmpty() || fixationData.isEmpty()) return;

        for (ProcessingFixation f : fixations) {
            List<double[]> points = new ArrayList<>();
            double sumDist  = 0.0;
            int    distCount = 0;

            for (GazePreprocessor.Sample s : fixationData) {
                if (s.originalIndex >= f.dataStart && s.originalIndex <= f.dataEnd) {
                    points.add(new double[]{ s.interpolatedGazeX, s.interpolatedGazeY });
                    if (!Double.isNaN(s.interpolatedDistance)) {
                        sumDist += s.interpolatedDistance;
                        distCount++;
                    }
                }
            }

            if (points.size() <= 1) { f.dispersion = null; continue; }

            double avgDist = distCount == 0 ? Double.NaN : sumDist / distCount;
            double[][] xy  = new double[points.size()][2];
            for (int i = 0; i < points.size(); i++) {
                xy[i][0] = points.get(i)[0];
                xy[i][1] = points.get(i)[1];
            }

            f.dispersion = dispersionDegree(
                    xy, new double[]{ f.centroidX, f.centroidY }, avgDist, dpi);
        }
    }

    private static List<ProcessingFixation> discardFixations(
            List<ProcessingFixation> fixations, long minDurationFixation) {
        List<ProcessingFixation> kept = new ArrayList<>();
        for (ProcessingFixation f : fixations) {
            if (f.duration >= minDurationFixation) kept.add(f);
        }
        for (int i = 0; i < kept.size(); i++) kept.get(i).id = i;
        return kept;
    }

    private static double calculateAngleBetweenFixations(ProcessingFixation f1,
                                                         ProcessingFixation f2) {
        double[] gaze0 = { f1.centroidXmm, f1.centroidYmm, f1.centroidZmm };
        double[] gaze1 = { f2.centroidXmm, f2.centroidYmm, f2.centroidZmm };
        double[] eye   = { f1.eyeXmm, f1.eyeYmm,
                (f1.lastEyeZmm + f2.firstEyeZmm) / 2.0 };
        return angleDegrees(vecSub(gaze0, eye), vecSub(gaze1, eye));
    }

    private static List<int[]> extractRuns(List<Integer> eventType, int valueToMatch) {
        List<int[]> runs = new ArrayList<>();
        if (eventType.isEmpty()) return runs;
        int start   = 0;
        int current = eventType.get(0);
        for (int i = 1; i < eventType.size(); i++) {
            if (!eventType.get(i).equals(current)) {
                if (current == valueToMatch) runs.add(new int[]{ start, i - 1 });
                start   = i;
                current = eventType.get(i);
            }
        }
        if (current == valueToMatch) runs.add(new int[]{ start, eventType.size() - 1 });
        return runs;
    }

    private static double meanInterpolatedX(List<GazePreprocessor.Sample> data,
                                            int start, int end) {
        double sum = 0.0; int count = 0;
        for (int i = start; i <= end; i++) {
            double v = data.get(i).interpolatedGazeX;
            if (!Double.isNaN(v)) { sum += v; count++; }
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    private static double meanInterpolatedY(List<GazePreprocessor.Sample> data,
                                            int start, int end) {
        double sum = 0.0; int count = 0;
        for (int i = start; i <= end; i++) {
            double v = data.get(i).interpolatedGazeY;
            if (!Double.isNaN(v)) { sum += v; count++; }
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    static double meanInterpolatedDistance(List<GazePreprocessor.Sample> data,
                                           int start, int end) {
        double sum = 0.0; int count = 0;
        for (int i = start; i <= end; i++) {
            double v = data.get(i).interpolatedDistance;
            if (!Double.isNaN(v)) { sum += v; count++; }
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    private static double meanGazeXmm(List<GazePreprocessor.Sample> data,
                                      int start, int end) {
        double sum = 0.0; int count = 0;
        for (int i = start; i <= end; i++) {
            Double v = data.get(i).gazeXmm;
            if (v != null && !Double.isNaN(v)) { sum += v; count++; }
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    private static double meanGazeYmm(List<GazePreprocessor.Sample> data,
                                      int start, int end) {
        double sum = 0.0; int count = 0;
        for (int i = start; i <= end; i++) {
            Double v = data.get(i).gazeYmm;
            if (v != null && !Double.isNaN(v)) { sum += v; count++; }
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    private static double toMm(double px, double dpi) {
        return px / dpi * 25.4;
    }

    static double angleDegrees(double[] v1, double[] v2) {
        double dot = v1[0]*v2[0] + v1[1]*v2[1] + v1[2]*v2[2];
        double n1  = Math.sqrt(v1[0]*v1[0] + v1[1]*v1[1] + v1[2]*v1[2]);
        double n2  = Math.sqrt(v2[0]*v2[0] + v2[1]*v2[1] + v2[2]*v2[2]);
        if (n1 == 0 || n2 == 0) return Double.NaN;
        double cos     = dot / (n1 * n2);
        if (Math.abs(cos) > 1.0) cos = Math.signum(cos);
        double rounded = Math.round(cos * 1e10) / 1e10;
        return Math.acos(rounded) * 180.0 / Math.PI;
    }

    private static double distanceToVisualAngle(double sizeMm, double screenDistanceMm) {
        if (Double.isNaN(screenDistanceMm) || screenDistanceMm <= 0) return Double.NaN;
        return 2 * Math.atan(sizeMm / (2 * screenDistanceMm)) * 180.0 / Math.PI;
    }

    private static double dispersionDegree(double[][] fixationXY,
                                           double[] centroidXY,
                                           double screenDistance,
                                           double dpi) {
        double sum = 0.0; int count = 0;
        for (double[] pt : fixationXY) {
            if (Double.isNaN(pt[0]) || Double.isNaN(pt[1])) continue;
            double dx = pt[0] - centroidXY[0];
            double dy = pt[1] - centroidXY[1];
            sum += dx*dx + dy*dy;
            count++;
        }
        if (count == 0) return Double.NaN;
        return distanceToVisualAngle(toMm(Math.sqrt(sum / count), dpi), screenDistance);
    }

    private static double amplitudeDegree(double startX, double startY,
                                          double endX,   double endY,
                                          double screenDistance, double dpi) {
        double amplitude = Math.sqrt(
                (endX - startX) * (endX - startX) +
                        (endY - startY) * (endY - startY)
        );
        return distanceToVisualAngle(toMm(amplitude, dpi), screenDistance);
    }

    private static double directionAngle(double startX, double startY,
                                         double endX,   double endY) {
        double dy    = endY - startY;
        double dx    = endX - startX;
        double theta = Math.atan2(dy, dx) * 180.0 / Math.PI;
        if (theta < 0) theta += 360.0;
        return theta;
    }

    private static double[] vecSub(double[] a, double[] b) {
        return new double[]{ a[0]-b[0], a[1]-b[1], a[2]-b[2] };
    }
}