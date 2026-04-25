# flink-ivt

A validated real-time fixation and saccade detection library for Apache Flink, implementing the I-VT (Velocity-Threshold Identification) algorithm.

Validated against the iMotions R reference implementation with 0.0ms start time difference.

---

## How it works

Gaze samples stream in from any source. The pipeline buffers them using Flink stateful processing and fires an event-time timer every **1000ms**, slicing the last **2000ms** of samples into a complete window — exactly mirroring the R reference implementation.

```
gaze samples
  → FixationFunction (2000ms window / 1000ms step)
      → GazePreprocessor (eye averaging, gap fill, interpolation)
      → IVTProcessor (velocity, classify, fixations, saccades)
          → fixations (main output)
          → saccades (side output)
```

---

## Quickstart

### With Kafka

Start Kafka:
```bash
docker-compose up -d kafka zookeeper
```

Run the pipeline:
```java
StreamExecutionEnvironment env =
    StreamExecutionEnvironment.getExecutionEnvironment();

IvtParams params = new IvtParams()
    .withScreenResolution(1920, 1080)
    .withMonitorSize(24.0)
    .withVelocityThreshold(30.0)
    .withMinFixationDuration(60)
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
```

Send gaze samples to `gaze-raw` as JSON:
```json
{
  "sessionId": "participant-01",
  "ts": 1234567890,
  "gazeLeftX": 960.0,
  "gazeLeftY": 540.0,
  "gazeRightX": 960.0,
  "gazeRightY": 540.0,
  "distLeft": 600.0,
  "distRight": 600.0,
  "screenW": 1920.0,
  "screenH": 1080.0,
  "monitorInches": 24.0,
  "originalTimestamp": 1234567890.0
}
```

### Without Kafka

```java
DataStream<GazeEvent> gazeStream = ...; // any source

IVTStream result = IVTPipeline.fromStream(gazeStream, new IvtParams());

result.fixations().print();
result.saccades().print();
```

---

## Configuration

| Parameter | Default | Description |
|---|---|---|
| `screenResolutionWidth` | 1920 | Screen width in pixels |
| `screenResolutionHeight` | 1080 | Screen height in pixels |
| `monitorSize` | 24.0 | Monitor size in inches |
| `velocityThreshold` | 30.0 | IVT threshold in deg/s |
| `windowVelocity` | 20 | Velocity window in ms |
| `minFixationDuration` | 60 | Minimum fixation duration in ms |
| `gapFill` | true | Fill short gaps in gaze signal |
| `maxGapLength` | 75 | Maximum fillable gap in ms |
| `noiseReduction` | false | Apply moving filter |
| `mergeFixation` | true | Merge nearby fixations |
| `maxTimeBtwFixation` | 75 | Max time between fixations to merge in ms |
| `maxAngleBtwFixation` | 0.5 | Max angle between fixations to merge in degrees |
| `detectSaccades` | true | Detect saccades |

---

## Output

**FixationEvent**

| Field | Type | Description |
|---|---|---|
| `fixationStart` | double | Start time in ms |
| `fixationEnd` | double | End time in ms |
| `fixationDuration` | double | Duration in ms |
| `fixationX` | double | Centroid X in pixels |
| `fixationY` | double | Centroid Y in pixels |
| `dispersion` | double | Spatial dispersion in degrees |

**SaccadeEvent**

| Field | Type | Description |
|---|---|---|
| `saccadeStart` | double | Start time in ms |
| `saccadeEnd` | double | End time in ms |
| `saccadeDuration` | double | Duration in ms |
| `amplitude` | double | Amplitude in degrees |
| `dirAngle` | double | Direction in degrees (0–360) |
| `peakVelocity` | double | Peak velocity in deg/s |
| `peakAcceleration` | double | Peak acceleration in deg/s² |
| `peakDeceleration` | double | Peak deceleration in deg/s² |

---

## Requirements

- Java 11+
- Apache Flink 1.17.1
- Apache Kafka 3.x (optional)

---

## License

Apache License 2.0