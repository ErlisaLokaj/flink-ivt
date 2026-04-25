package com.flinkivt.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

/**
 * A single raw gaze sample from an eye tracker, representing the primary
 * input to the IVT pipeline. Fields match the column names expected by
 * the R reference implementation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GazeEvent implements Serializable {

    /** Session identifier used to key the Flink stream. */
    @JsonProperty("sessionId")
    public String sessionId;

    /** Event timestamp in milliseconds, used for Flink event-time processing. */
    @JsonProperty("ts")
    public Long ts;

    /** Gaze coordinates in pixels for left and right eye. */
    @JsonProperty("gazeLeftX")  public Double gazeLeftX;
    @JsonProperty("gazeLeftY")  public Double gazeLeftY;
    @JsonProperty("gazeRightX") public Double gazeRightX;
    @JsonProperty("gazeRightY") public Double gazeRightY;

    /** Eye-to-screen distance in mm, used for visual angle computation. */
    @JsonProperty("distLeft")  public Double distLeft;
    @JsonProperty("distRight") public Double distRight;

    /** Screen dimensions in pixels and physical monitor size in inches. */
    @JsonProperty("screenW")       public Double screenW = 1920.0;
    @JsonProperty("screenH")       public Double screenH = 1080.0;
    @JsonProperty("monitorInches") public Double monitorInches = 24.0;

    /** Original hardware timestamp from the eye tracker. */
    @JsonProperty("originalTimestamp")
    public Double originalTimestamp;

    /** Pupil diameter in mm for each eye. Null if unavailable. */
    @JsonProperty("PupilLeftDiameter")  public Float pupilLeftDiameter;
    @JsonProperty("PupilRightDiameter") public Float pupilRightDiameter;

    /** Validity flags from the eye tracker (1 = valid, 0 = invalid). */
    @JsonProperty("ValidityLeft")  public Integer validityLeft;
    @JsonProperty("ValidityRight") public Integer validityRight;

    public GazeEvent() {}

    @Override
    public String toString() {
        return String.format(
                "GazeEvent{ts=%d, sessionId='%s', " +
                        "leftGaze=[%.1f,%.1f], rightGaze=[%.1f,%.1f], " +
                        "dist=[%.1f,%.1f]}",
                ts, sessionId,
                gazeLeftX, gazeLeftY,
                gazeRightX, gazeRightY,
                distLeft, distRight
        );
    }
}