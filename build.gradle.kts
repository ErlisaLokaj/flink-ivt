plugins {
    java
}

allprojects {
    group = "com.flinkivt"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    java {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    dependencies {
        implementation("org.apache.flink:flink-streaming-java:1.18.1")
        implementation("org.apache.flink:flink-connector-kafka:3.2.0-1.18")
        implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
        compileOnly("org.projectlombok:lombok:1.18.30")
        annotationProcessor("org.projectlombok:lombok:1.18.30")
        testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}