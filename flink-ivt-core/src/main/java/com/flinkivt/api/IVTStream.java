package com.flinkivt.api;

import com.flinkivt.model.FixationEvent;
import com.flinkivt.model.SaccadeEvent;
import org.apache.flink.streaming.api.datastream.DataStream;

/**
 * Result of running the IVT pipeline on a gaze stream.
 * Provides access to detected fixations and saccades as Flink DataStreams.
 */
public class IVTStream {

    private final DataStream<FixationEvent> fixations;
    private final DataStream<SaccadeEvent> saccades;

    public IVTStream(DataStream<FixationEvent> fixations,
                     DataStream<SaccadeEvent> saccades) {
        this.fixations = fixations;
        this.saccades  = saccades;
    }

    /** Returns the stream of detected fixation events. */
    public DataStream<FixationEvent> fixations() {
        return fixations;
    }

    /** Returns the stream of detected saccade events. */
    public DataStream<SaccadeEvent> saccades() {
        return saccades;
    }
}