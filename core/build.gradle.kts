import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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

    implementation("org.bytedeco:javacv:1.5.9") {
        exclude("org.bytedeco", "artoolkitplus")
        exclude("org.bytedeco", "openblas")
        exclude("org.bytedeco", "opencv")
        exclude("org.bytedeco", "flandmark")
        exclude("org.bytedeco", "flycapture")
        exclude("org.bytedeco", "leptonica")
        exclude("org.bytedeco", "libdc1394")
        exclude("org.bytedeco", "libfreenect")
        exclude("org.bytedeco", "libfreenect2")
        exclude("org.bytedeco", "librealsense")
        exclude("org.bytedeco", "librealsense2")
        exclude("org.bytedeco", "tesseract")
        exclude("org.bytedeco", "videoinput")
        exclude("org.hamcrest", "hamcrest-core")
    }

    implementation("org.bytedeco:ffmpeg:5.1.2-1.5.8:android-arm64")
    implementation("org.bytedeco:ffmpeg:5.1.2-1.5.8:android-x86_64")
    implementation("org.bytedeco:ffmpeg:5.1.2-1.5.8:windows-x86_64")

    implementation("org.bytedeco:openblas-platform:0.3.23-1.5.9")
    implementation("org.bytedeco:opencv-platform:4.7.0-1.5.9")

    testImplementation("io.mockk:mockk:1.4.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.0")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "15"
}