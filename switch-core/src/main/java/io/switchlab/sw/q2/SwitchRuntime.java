package io.switchlab.sw.q2;

import io.switchlab.iso.Channels;
import io.switchlab.sw.ListenerSpec;
import io.switchlab.sw.listener.CaptureListener;
import io.switchlab.sw.participant.NetworkManagement;
import io.switchlab.sw.participant.SendResponse;
import io.switchlab.sw.participant.Unsupported;
import io.switchlab.sw.tm.MtiGroupSelector;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.jpos.q2.Q2;

/** Generates the Q2 deploy directory from listener config and runs Q2 inside the host JVM. */
public class SwitchRuntime {
  private static final String QUEUE = "txnmgr";

  private final Path deployDir;
  private final List<ListenerSpec> listeners;
  private Q2 q2;

  public SwitchRuntime(Path deployDir, List<ListenerSpec> listeners) {
    this.deployDir = deployDir;
    this.listeners = List.copyOf(listeners);
  }

  public void start(Duration timeout) {
    writeDeployDir();
    q2 = new Q2(deployDir.toString());
    q2.start();
    awaitListening(timeout);
  }

  public void stop() {
    if (q2 != null) {
      q2.shutdown(true);
      q2 = null;
    }
  }

  private void writeDeployDir() {
    try {
      Files.createDirectories(deployDir);
      try (var stale = Files.list(deployDir)) {
        for (Path p : stale.filter(f -> f.toString().endsWith(".xml")).toList()) {
          Files.delete(p);
        }
      }
      Files.writeString(deployDir.resolve("00_logger.xml"), loggerXml());
      Files.writeString(deployDir.resolve("10_txnmgr.xml"), txnmgrXml());
      for (ListenerSpec l : listeners) {
        Files.writeString(
            deployDir.resolve("20_server_" + l.dialect().code() + ".xml"), serverXml(l));
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void awaitListening(Duration timeout) {
    Instant deadline = Instant.now().plus(timeout);
    for (ListenerSpec l : listeners) {
      while (!accepts(l.port())) {
        if (Instant.now().isAfter(deadline)) {
          throw new IllegalStateException("Listener " + l + " did not start in " + timeout);
        }
        try {
          Thread.sleep(50);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(e);
        }
      }
    }
  }

  private static boolean accepts(int port) {
    try (Socket s = new Socket()) {
      s.connect(new InetSocketAddress("127.0.0.1", port), 200);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private static String loggerXml() {
    return """
        <logger name="Q2" class="org.jpos.q2.qbean.LoggerAdaptor">
          <log-listener class="org.jpos.util.FilterLogListener">
            <property name="priority" value="warn"/>
          </log-listener>
        </logger>
        """;
  }

  private static String txnmgrXml() {
    return """
        <txnmgr name="txnmgr" class="org.jpos.transaction.TransactionManager" logger="Q2">
          <property name="queue" value="%s"/>
          <property name="sessions" value="16"/>
          <participant class="%s" logger="Q2"/>
          <group name="netmgmt">
            <participant class="%s" logger="Q2"/>
          </group>
          <group name="unsupported">
            <participant class="%s" logger="Q2"/>
          </group>
          <participant class="%s" logger="Q2"/>
        </txnmgr>
        """
        .formatted(
            QUEUE,
            MtiGroupSelector.class.getName(),
            NetworkManagement.class.getName(),
            Unsupported.class.getName(),
            SendResponse.class.getName());
  }

  private static String serverXml(ListenerSpec l) {
    return """
        <server name="server-%1$s" class="org.jpos.q2.iso.QServer" logger="Q2">
          <attr name="port" type="java.lang.Integer">%2$d</attr>
          <channel class="%3$s" packager="%4$s" logger="Q2"/>
          <request-listener class="%5$s" logger="Q2">
            <property name="queue" value="%6$s"/>
            <property name="dialect" value="%1$s"/>
          </request-listener>
        </server>
        """
        .formatted(
            l.dialect().code(),
            l.port(),
            Channels.channelClass(l.dialect()).getName(),
            l.dialect().packagerClass().getName(),
            CaptureListener.class.getName(),
            QUEUE);
  }
}
