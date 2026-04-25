package com.flinkivt.examples;

import com.flinkivt.api.IVTPipeline;
import com.flinkivt.model.IvtParams;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/**
 * Example showing how to run the IVT pipeline with Kafka as source and sink.
 *
 * <p>Prerequisites:
 * - Kafka running on localhost:9092
 * - Topic "gaze-raw" receiving gaze samples from an eye tracker
 *
 * <p>Run with Docker Compose:
 * <pre>
 *   docker-compose up
 * </pre>
 */
public class KafkaToKafkaExample {

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

        IVTPipeline.builder()
                .env(env)
                .params(params)
                .bootstrapServers("localhost:9092")
                .inputTopic("gaze-raw")
                .fixationTopic("gaze-fixations")
                .saccadeTopic("gaze-saccades")
                .build()
                .run();
    }
}