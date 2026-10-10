package io.switchlab.iso;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.jpos.core.Configuration;
import org.jpos.iso.ISOException;
import org.jpos.iso.packager.GenericPackager;

public class NibssPackager extends GenericPackager {
  public NibssPackager() throws ISOException {
    super();
    try (InputStream in = NibssPackager.class.getResourceAsStream("nibss-pos.xml")) {
      readFile(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Field definitions come from the bundled XML; Q2 must not ask for a packager-config file. */
  @Override
  public void setConfiguration(Configuration cfg) {}
}
