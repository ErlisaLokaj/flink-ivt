description = "Flink IVT Examples"

dependencies {
    implementation(project(":flink-ivt-core"))
    implementation("org.apache.flink:flink-java:1.18.1")
    implementation("org.apache.flink:flink-streaming-java:1.18.1")
    implementation("org.apache.flink:flink-clients:1.18.1")
    implementation("org.apache.flink:flink-connector-kafka:3.2.0-1.18")
    implementation("org.apache.flink:flink-connector-base:1.18.1")
    implementation("org.apache.flink:flink-shaded-jackson:2.14.2-17.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.14.2")
    runtimeOnly("org.slf4j:slf4j-simple:1.7.36")
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "11"
        targetCompatibility = "11"
    }

    register<JavaExec>("runFlinkJob") {
        group = "flink"
        description = "Run IVT Flink Pipeline"
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set("com.flinkivt.examples.KafkaToKafkaExample")

        jvmArgs = listOf(
            "-Xmx1g",
            "-XX:+UseG1GC",
            "-Dorg.slf4j.simpleLogger.defaultLogLevel=INFO",
            "-Dflink.execution.mode=STREAMING",
            "--add-opens", "java.base/java.lang=ALL-UNNAMED",
            "--add-opens", "java.base/java.util=ALL-UNNAMED",
            "--add-opens", "java.base/java.util.concurrent=ALL-UNNAMED",
            "--add-opens", "java.base/java.io=ALL-UNNAMED",
            "--add-opens", "java.base/java.net=ALL-UNNAMED"
        )
    }
}