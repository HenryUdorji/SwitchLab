package io.switchlab.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SwitchLabApplication {
  public static void main(String[] args) {
    SpringApplication.run(SwitchLabApplication.class, args);
  }
}
