package io.switchlab.client;

import io.switchlab.iso.Channels;
import io.switchlab.iso.Dialect;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Optional;
import org.jpos.iso.BaseChannel;
import org.jpos.iso.ISOChannel;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;

public class PosClient implements AutoCloseable {
  private final Dialect dialect;
  private final ISOChannel channel;

  private PosClient(Dialect dialect, ISOChannel channel) {
    this.dialect = dialect;
    this.channel = channel;
  }

  public static PosClient connect(Dialect dialect, String host, int port) throws IOException {
    ISOChannel channel = Channels.client(dialect, host, port);
    channel.connect();
    return new PosClient(dialect, channel);
  }

  public Dialect dialect() {
    return dialect;
  }

  public void send(ISOMsg request) throws IOException, ISOException {
    channel.send(request);
  }

  /** Waits up to {@code timeoutMs} for the next message, as a terminal does after sending. */
  public Optional<ISOMsg> receive(int timeoutMs) throws IOException, ISOException {
    ((BaseChannel) channel).setTimeout(timeoutMs);
    try {
      return Optional.of(channel.receive());
    } catch (SocketTimeoutException e) {
      return Optional.empty();
    }
  }

  public Optional<ISOMsg> request(ISOMsg request, int timeoutMs) throws IOException, ISOException {
    send(request);
    return receive(timeoutMs);
  }

  @Override
  public void close() throws IOException {
    channel.disconnect();
  }
}
