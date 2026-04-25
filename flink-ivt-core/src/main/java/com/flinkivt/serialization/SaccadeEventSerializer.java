package com.flinkivt.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flinkivt.model.SaccadeEvent;
import org.apache.flink.api.common.serialization.SerializationSchema;

/**
 * Serializes {@link SaccadeEvent} objects to JSON bytes for Kafka output.
 */
public class SaccadeEventSerializer implements SerializationSchema<SaccadeEvent> {

    private static final ObjectMapper om = new ObjectMapper();

    @Override
    public byte[] serialize(SaccadeEvent event) {
        try {
            return om.writeValueAsBytes(event);
        } catch (Exception e) {
            System.err.println("Failed to serialize SaccadeEvent: " + e.getMessage());
            return "{}".getBytes();
        }
    }
}