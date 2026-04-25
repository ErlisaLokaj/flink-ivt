package com.flinkivt.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flinkivt.model.GazeEvent;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;

import java.io.IOException;

/**
 * Deserializes JSON bytes from Kafka into {@link GazeEvent} objects.
 * Used as the Kafka source deserializer in the IVT pipeline.
 */
public class GazeEventDeserializer implements DeserializationSchema<GazeEvent> {

    private static final ObjectMapper om = new ObjectMapper();

    @Override
    public GazeEvent deserialize(byte[] message) throws IOException {
        try {
            return om.readValue(message, GazeEvent.class);
        } catch (Exception e) {
            System.err.println("Failed to deserialize GazeEvent: " + e.getMessage());
            return null;
        }
    }

    @Override
    public boolean isEndOfStream(GazeEvent nextElement) {
        return nextElement == null;
    }

    @Override
    public TypeInformation<GazeEvent> getProducedType() {
        return TypeInformation.of(GazeEvent.class);
    }
}