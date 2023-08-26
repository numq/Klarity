plugins {
    kotlin("jvm") version "1.8.0"
}

group = "com.github.numq"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    implementation("org.bytedeco:javacv:1.5.9")
    implementation("org.bytedeco:javacpp:1.5.9")
    implementation("org.bytedeco:ffmpeg-platform:6.0-1.5.9")
    implementation("org.bytedeco.javacpp-presets:ffmpeg:4.1-1.4.4")
    implementation("org.bytedeco:openblas:0.3.23-1.5.9")
    implementation("org.bytedeco:openblas-platform:0.3.23-1.5.9")
    implementation("org.bytedeco:opencv:4.7.0-1.5.9")
    implementation("org.bytedeco:opencv-platform:4.7.0-1.5.9")

    testImplementation("io.mockk:mockk:1.4.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.0")
}

tasks.test {
    useJUnitPlatform()
}