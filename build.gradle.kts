plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spotless)
}

allprojects {
    group = "io.switchlab"
    version = "0.1.0-SNAPSHOT"
    repositories { mavenCentral() }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "com.diffplug.spotless")

    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion = JavaLanguageVersion.of(25) }
    }

    dependencies {
        "implementation"(platform(rootProject.libs.spring.boot.bom))
        "testImplementation"(platform(rootProject.libs.spring.boot.bom))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testImplementation"("org.assertj:assertj-core")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> { useJUnitPlatform() }

    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java { googleJavaFormat() }
    }
}
