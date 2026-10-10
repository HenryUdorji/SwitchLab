package io.switchlab.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.switchlab.app.SwitchLabApplication;
import io.switchlab.client.Messages;
import io.switchlab.client.PosClient;
import io.switchlab.iso.Dialect;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(classes = SwitchLabApplication.class)
@Testcontainers
class EchoIT {
  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  private static final Map<Dialect, Integer> PORTS =
      Map.of(Dialect.GENERIC87, freePort(), Dialect.NIBSS, freePort());

  @DynamicPropertySource
  static void listenerPorts(DynamicPropertyRegistry registry) {
    registry.add("spring.flyway.placeholders.generic87Port", () -> PORTS.get(Dialect.GENERIC87));
    registry.add("spring.flyway.placeholders.nibssPort", () -> PORTS.get(Dialect.NIBSS));
  }

  @ParameterizedTest
  @EnumSource(Dialect.class)
  void echoIsAnsweredOnTheDialectsListener(Dialect dialect) throws Exception {
    try (PosClient client = PosClient.connect(dialect, "localhost", PORTS.get(dialect))) {
      ISOMsg response =
          client.request(Messages.echo(dialect, "000042", "2039ABCD"), 5000).orElseThrow();

      assertThat(response.getMTI()).isEqualTo("0810");
      assertThat(response.getString(39)).isEqualTo("00");
      assertThat(response.getString(11)).isEqualTo("000042");
    }
  }

  @Test
  void nibssKeyDownloadIsRejectedUntilImplemented() throws Exception {
    ISOMsg request = Messages.echo(Dialect.NIBSS, "000043", "2039ABCD");
    request.set(3, "9A0000");
    try (PosClient client =
        PosClient.connect(Dialect.NIBSS, "localhost", PORTS.get(Dialect.NIBSS))) {
      ISOMsg response = client.request(request, 5000).orElseThrow();

      assertThat(response.getString(39)).isEqualTo("40");
    }
  }

  private static int freePort() {
    try (ServerSocket s = new ServerSocket(0)) {
      return s.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
