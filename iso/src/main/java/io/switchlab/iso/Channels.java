package io.switchlab.iso;

import org.jpos.iso.ISOChannel;
import org.jpos.iso.channel.NACChannel;

/** Both built-in dialects frame messages with a 2-byte binary length header and no TPDU. */
public final class Channels {
  private Channels() {}

  public static Class<? extends ISOChannel> channelClass(Dialect dialect) {
    return NACChannel.class;
  }

  public static ISOChannel client(Dialect dialect, String host, int port) {
    return new NACChannel(host, port, dialect.newPackager(), null);
  }
}
