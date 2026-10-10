package io.switchlab.client;

import io.switchlab.iso.Dialect;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;

public final class Messages {
  private static final DateTimeFormatter F7 = DateTimeFormatter.ofPattern("MMddHHmmss");
  private static final DateTimeFormatter F12 = DateTimeFormatter.ofPattern("HHmmss");
  private static final DateTimeFormatter F13 = DateTimeFormatter.ofPattern("MMdd");

  private Messages() {}

  public static ISOMsg echo(Dialect dialect, String stan, String tid) throws ISOException {
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    ISOMsg m = new ISOMsg("0800");
    m.set(7, F7.format(now));
    m.set(11, stan);
    switch (dialect) {
      case NIBSS -> {
        m.set(3, "9D0000");
        m.set(12, F12.format(now));
        m.set(13, F13.format(now));
        m.set(41, tid);
      }
      case GENERIC87 -> m.set(70, "301");
    }
    return m;
  }
}
