package com.flinkivt.functions;

import com.flinkivt.model.FixationEvent;
import com.flinkivt.model.GazeEvent;
import com.flinkivt.model.IVTResult;
import com.flinkivt.model.IvtParams;
import com.flinkivt.model.SaccadeEvent;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Core Flink operator for real-time fixation and saccade detection using the IVT algorithm.
 *
 * <p>Buffers incoming gaze samples and fires an event-time timer every {@code STEP_MS}
 * milliseconds. On each timer, it slices the last {@code WINDOW_MS} of buffered samples
 * into a complete window and runs the full IVT pipeline — matching the windowing
 * structure of the R reference implementation exactly.
 *
 * <p>Fixations are emitted to the main output stream.
 * Saccades are emitted via a Flink side output using {@link #SACCADE_OUTPUT_TAG}.
 */
public class FixationFunction extends KeyedProcessFunction<String, GazeEvent, FixationEvent> {

    /** Window size in milliseconds. */
    private static final long WINDOW_MS = 2000L;

    /** Step size in milliseconds. */
    private static final long STEP_MS = 1000L;

    /** How long to keep samples in the buffer beyond the current window. */
    private static final long BUFFER_KEEP_MS = 15000L;

    /** Side output tag for saccade events. */
    public static final OutputTag<SaccadeEvent> SACCADE_OUTPUT_TAG =
            new OutputTag<SaccadeEvent>("saccades", TypeInformation.of(SaccadeEvent.class));

    private final IvtParams params;

    private transient ListState<GazeEvent> rawBuffer;
    private transient ValueState<Long> nextTimerTs;
    private transient ValueState<Long> firstSampleTs;
    private transient ValueState<Integer> fixationCounter;

    public FixationFunction(IvtParams params) {
        this.params = params;
    }

    @Override
    public void open(Configuration parameters) {
        rawBuffer = getRuntimeContext().getListState(
                new ListStateDescriptor<>("raw-gaze-buffer",
                        TypeInformation.of(GazeEvent.class)));
        nextTimerTs = getRuntimeContext().getState(
                new ValueStateDescriptor<>("next-fixation-timer", Long.class));
        firstSampleTs = getRuntimeContext().getState(
                new ValueStateDescriptor<>("first-sample-ts", Long.class));
        fixationCounter = getRuntimeContext().getState(
                new ValueStateDescriptor<>("fixation-counter", Integer.class));
    }

    @Override
    public void processElement(GazeEvent event, Context ctx,
                               Collector<FixationEvent> out) throws Exception {
        if (event == null || event.ts == null) return;

        Long firstTs = firstSampleTs.value();
        if (firstTs == null) {
            firstTs = event.ts;
            firstSampleTs.update(firstTs);
        }

        List<GazeEvent> buffer = asList(rawBuffer);
        buffer.add(event);
        buffer.sort(Comparator.comparingLong(e -> e.ts));

        long cutoff = event.ts - BUFFER_KEEP_MS;
        buffer.removeIf(e -> e.ts != null && e.ts < cutoff);
        rawBuffer.update(buffer);

        long nextBoundary;
        if (event.ts < firstTs + WINDOW_MS) {
            nextBoundary = firstTs + WINDOW_MS;
        } else {
            long stepsSinceFirstWindowEnd = (event.ts - (firstTs + WINDOW_MS)) / STEP_MS;
            nextBoundary = firstTs + WINDOW_MS + (stepsSinceFirstWindowEnd + 1) * STEP_MS;
        }

        Long registeredTimer = nextTimerTs.value();
        if (registeredTimer == null || nextBoundary > registeredTimer) {
            ctx.timerService().registerEventTimeTimer(nextBoundary);
            nextTimerTs.update(nextBoundary);
        }
    }

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx,
                        Collector<FixationEvent> out) throws Exception {
        List<GazeEvent> buffer = asList(rawBuffer);
        if (buffer.isEmpty()) {
            nextTimerTs.clear();
            return;
        }

        buffer.sort(Comparator.comparingLong(e -> e.ts));

        long windowStart = timestamp - WINDOW_MS;
        long windowEnd   = timestamp;

        List<GazeEvent> window = new ArrayList<>();
        for (GazeEvent e : buffer) {
            if (e.ts != null && e.ts >= windowStart && e.ts < windowEnd) {
                window.add(e);
            }
        }

        if (!window.isEmpty()) {
            Double diffTimestamps = GazePreprocessor.computeAvgTimestampDiff(window);
            if (diffTimestamps != null && diffTimestamps > 0) {
                List<GazePreprocessor.Sample> processed =
                        GazePreprocessor.process(window, params, diffTimestamps);
                IVTResult ivtResult =
                        IVTProcessor.detect(processed, params, diffTimestamps);

                Integer counter = fixationCounter.value();
                if (counter == null) counter = 1;
                for (FixationEvent fixation : ivtResult.fixations) {
                    fixation.fixID = counter++;
                    out.collect(fixation);
                }
                fixationCounter.update(counter);

                for (SaccadeEvent saccade : ivtResult.saccades) {
                    ctx.output(SACCADE_OUTPUT_TAG, saccade);
                }
            }
        }

        long cutoff = timestamp - BUFFER_KEEP_MS;
        buffer.removeIf(e -> e.ts != null && e.ts < cutoff);
        rawBuffer.update(buffer);

        long next = timestamp + STEP_MS;
        ctx.timerService().registerEventTimeTimer(next);
        nextTimerTs.update(next);
    }

    private static List<GazeEvent> asList(ListState<GazeEvent> state) throws Exception {
        List<GazeEvent> list = new ArrayList<>();
        for (GazeEvent e : state.get()) list.add(e);
        return list;
    }
}