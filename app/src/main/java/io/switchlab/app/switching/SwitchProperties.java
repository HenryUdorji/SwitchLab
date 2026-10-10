package io.switchlab.app.switching;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("switchlab.switch")
public record SwitchProperties(Path deployDir, Duration startupTimeout) {}
