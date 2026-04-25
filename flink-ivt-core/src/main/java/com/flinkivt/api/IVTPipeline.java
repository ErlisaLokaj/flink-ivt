package com.flinkivt.api;

import com.flinkivt.functions.FixationFunction;
import com.flinkivt.model.FixationEvent;
import com.flinkivt.model.GazeEvent;
import com.flinkivt.model.IvtParams;
import com.flinkivt.model.SaccadeEvent;
import com.flinkivt.serialization.FixationEventSerializer;
import com.flinkivt.serialization.GazeEventDeserializer;
import com.flinkivt.serialization.SaccadeEventSerializer;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.time.Duration;
import java.util.UUID;

/**
 * Main entry point for the flink-ivt library.
 *
 * <p>Builds and wires the complete IVT detection pipeline:
 * Kafka source → preprocessing → IVT detection → Kafka sinks.
 *
 * <p>Example usage:
 * <pre>
 *   StreamExecutionEnvironment env =
 *       StreamExecutionEnvironment.getExecutionEnvironment();
 *
 *   IvtParams params = new IvtParams()
 *       .withScreenResolution(1920, 1080)
 *       .withMonitorSize(24.0)
 *       .withVelocityThreshold(30.0)
 *       .withMinFixationDuration(60)
 *       .withDetectSaccades(true);
 *
 *   IVTPipeline.builder()
 *       .env(env)
 *       .params(params)
 *       .bootstrapServers("localhost:9092")
 *       .inputTopic("gaze-raw")
 *       .fixationTopic("gaze-fixations")
 *       .saccadeTopic("gaze-saccades")
 *       .build()
 *       .run();
 * </pre>
 */
public class IVTPipeline {

    private final StreamExecutionEnvironment env;
    private final IvtParams params;
    private final String bootstrapServers;
    private final String inputTopic;
    private final String fixationTopic;
    private final String saccadeTopic;

    private IVTPipeline(Builder builder) {
        this.env              = builder.env;
        this.params           = builder.params;
        this.bootstrapServers = builder.bootstrapServers;
        this.inputTopic       = builder.inputTopic;
        this.fixationTopic    = builder.fixationTopic;
        this.saccadeTopic     = builder.saccadeTopic;
    }

    /**
     * Builds and executes the IVT pipeline.
     * Blocks until the pipeline finishes or is cancelled.
     */
    public void run() throws Exception {
        KafkaSource<GazeEvent> source = KafkaSource.<GazeEvent>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(inputTopic)
                .setGroupId("flink-ivt-" + UUID.randomUUID())
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new GazeEventDeserializer())
                .build();

        DataStream<GazeEvent> gaze = env.fromSource(
                source,
                WatermarkStrategy.<GazeEvent>forMonotonousTimestamps()
                        .withTimestampAssigner((e, ts) -> e.ts)
                        .withIdleness(Duration.ofSeconds(5)),
                "gaze-source"
        );

        SingleOutputStreamOperator<FixationEvent> fixations = gaze
                .filter(e -> e != null)
                .keyBy(e -> e.sessionId != null ? e.sessionId : "default")
                .process(new FixationFunction(params))
                .name("ivt-detection");

        DataStream<SaccadeEvent> saccades = fixations
                .getSideOutput(FixationFunction.SACCADE_OUTPUT_TAG);

        KafkaSink<FixationEvent> fixationSink = KafkaSink.<FixationEvent>builder()
                .setBootstrapServers(bootstrapServers)
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.<FixationEvent>builder()
                                .setTopic(fixationTopic)
                                .setValueSerializationSchema(new FixationEventSerializer())
                                .build()
                )
                .build();

        KafkaSink<SaccadeEvent> saccadeSink = KafkaSink.<SaccadeEvent>builder()
                .setBootstrapServers(bootstrapServers)
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.<SaccadeEvent>builder()
                                .setTopic(saccadeTopic)
                                .setValueSerializationSchema(new SaccadeEventSerializer())
                                .build()
                )
                .build();

        fixations.sinkTo(fixationSink).name("fixation-kafka-sink");
        saccades.sinkTo(saccadeSink).name("saccade-kafka-sink");

        env.execute("flink-ivt: Real-time IVT Fixation and Saccade Detection");
    }

    /**
     * Runs the IVT pipeline on an existing gaze stream without requiring Kafka.
     * Use this when you already have a {@link DataStream} of gaze events
     * from any source — file, socket, custom eye tracker API, etc.
     *
     * <p>Example:
     * <pre>
     *   DataStream&lt;GazeEvent&gt; gazeStream = ...; // your own source
     *
     *   IVTStream result = IVTPipeline.fromStream(gazeStream, new IvtParams());
     *
     *   result.fixations().print();  // do anything with fixations
     *   result.saccades().print();   // do anything with saccades
     * </pre>
     *
     * @param gazeStream  input stream of raw gaze samples
     * @param params      IVT configuration parameters
     * @return            fixation and saccade output streams
     */
    public static IVTStream fromStream(DataStream<GazeEvent> gazeStream, IvtParams params) {
        SingleOutputStreamOperator<FixationEvent> fixations = gazeStream
                .filter(e -> e != null)
                .keyBy(e -> e.sessionId != null ? e.sessionId : "default")
                .process(new FixationFunction(params))
                .name("ivt-detection");

        DataStream<SaccadeEvent> saccades = fixations
                .getSideOutput(FixationFunction.SACCADE_OUTPUT_TAG);

        return new IVTStream(fixations, saccades);
    }

    /**
     * Returns a new builder for constructing an {@link IVTPipeline}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link IVTPipeline}.
     */
    public static class Builder {

        private StreamExecutionEnvironment env;
        private IvtParams params = new IvtParams();
        private String bootstrapServers = "localhost:9092";
        private String inputTopic       = "gaze-raw";
        private String fixationTopic    = "gaze-fixations";
        private String saccadeTopic     = "gaze-saccades";

        public Builder env(StreamExecutionEnvironment env) {
            this.env = env;
            return this;
        }

        public Builder params(IvtParams params) {
            this.params = params;
            return this;
        }

        public Builder bootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
            return this;
        }

        public Builder inputTopic(String inputTopic) {
            this.inputTopic = inputTopic;
            return this;
        }

        public Builder fixationTopic(String fixationTopic) {
            this.fixationTopic = fixationTopic;
            return this;
        }

        public Builder saccadeTopic(String saccadeTopic) {
            this.saccadeTopic = saccadeTopic;
            return this;
        }

        public IVTPipeline build() {
            if (env == null) {
                throw new IllegalStateException(
                        "StreamExecutionEnvironment must be set. " +
                                "Call .env(StreamExecutionEnvironment.getExecutionEnvironment())");
            }
            return new IVTPipeline(this);
        }
    }
}