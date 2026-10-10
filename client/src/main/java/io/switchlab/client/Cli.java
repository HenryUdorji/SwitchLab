package io.switchlab.client;

import io.switchlab.iso.Dialect;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.jpos.iso.ISOMsg;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "switchlab-client",
    mixinStandardHelpOptions = true,
    subcommands = {Cli.Echo.class})
public class Cli {
  public static void main(String[] args) {
    System.exit(new CommandLine(new Cli()).execute(args));
  }

  static class Connection {
    @Option(names = "--host", defaultValue = "localhost")
    String host;

    @Option(names = "--port", required = true)
    int port;

    @Option(names = "--dialect", defaultValue = "nibss", description = "generic87 or nibss")
    String dialect;

    @Option(names = "--timeout-ms", defaultValue = "10000")
    int timeoutMs;
  }

  @Command(name = "echo", description = "Send a network management echo (0800) and print the 0810.")
  static class Echo implements Callable<Integer> {
    @CommandLine.Mixin Connection conn;

    @Option(names = "--tid", defaultValue = "20390001")
    String tid;

    @Option(names = "--stan", defaultValue = "000001")
    String stan;

    @Override
    public Integer call() throws Exception {
      Dialect dialect = Dialect.fromCode(conn.dialect);
      try (PosClient client = PosClient.connect(dialect, conn.host, conn.port)) {
        Optional<ISOMsg> response =
            client.request(Messages.echo(dialect, stan, tid), conn.timeoutMs);
        if (response.isEmpty()) {
          System.err.println("No response within " + conn.timeoutMs + " ms");
          return 2;
        }
        response.get().dump(System.out, "");
        return "00".equals(response.get().getString(39)) ? 0 : 1;
      }
    }
  }
}
