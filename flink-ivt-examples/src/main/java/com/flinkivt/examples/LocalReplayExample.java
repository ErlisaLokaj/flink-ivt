package com.flinkivt.examples;

import com.flinkivt.api.IVTPipeline;
import com.flinkivt.api.IVTStream;
import com.flinkivt.model.GazeEvent;
import com.flinkivt.model.IvtParams;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/**
 * Example showing how to run the IVT pipeline without Kafka.
 * Provide any DataStream of GazeEvents and get fixations and saccades back.
 *
 * <p>This is useful for:
 * - Processing recorded eye tracking data from files
 * - Integrating with custom eye tracker APIs
 * - Testing without Kafka infrastructure
 */
public class LocalReplayExample {

    public static void main(String[] args) throws Exception {

        StreamExecutionEnvironment env =
                StreamExecutionEnvironment.getExecutionEnvironment();

        IvtParams params = new IvtParams()
                .withScreenResolution(1920, 1080)
                .withMonitorSize(24.0)
                .withVelocityThreshold(30.0)
                .withMinFixationDuration(60)
                .withMergeFixation(true, 75, 0.5)
                .withDetectSaccades(true);

        // Bring your own DataStream<GazeEvent> from any source
        // This example uses a simple collection for demonstration
        DataStream<GazeEvent> gazeStream = env.fromElements(new GazeEvent());

        IVTStream result = IVTPipeline.fromStream(gazeStream, params);

        result.fixations().print().name("fixations-output");
        result.saccades().print().name("saccades-output");

        env.execute("flink-ivt: Local Replay Example");
    }
}