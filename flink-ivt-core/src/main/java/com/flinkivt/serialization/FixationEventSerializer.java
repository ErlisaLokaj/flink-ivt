package com.flinkivt.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flinkivt.model.FixationEvent;
import org.apache.flink.api.common.serialization.SerializationSchema;

/**
 * Serializes {@link FixationEvent} objects to JSON bytes for Kafka output.
 */
public class FixationEventSerializer implements SerializationSchema<FixationEvent> {

    private static final ObjectMapper om = new ObjectMapper();

    @Override
    public byte[] serialize(FixationEvent event) {
        try {
            return om.writeValueAsBytes(event);
        } catch (Exception e) {
            System.err.println("Failed to serialize FixationEvent: " + e.getMessage());
            return "{}".getBytes();
        }
    }
}