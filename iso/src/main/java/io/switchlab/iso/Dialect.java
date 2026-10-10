package io.switchlab.iso;

import java.util.Arrays;
import org.jpos.iso.ISOPackager;
import org.jpos.iso.packager.ISO87APackager;

public enum Dialect {
  GENERIC87("generic87", ISO87APackager.class),
  NIBSS("nibss", NibssPackager.class);

  private final String code;
  private final Class<? extends ISOPackager> packagerClass;

  Dialect(String code, Class<? extends ISOPackager> packagerClass) {
    this.code = code;
    this.packagerClass = packagerClass;
  }

  public String code() {
    return code;
  }

  public Class<? extends ISOPackager> packagerClass() {
    return packagerClass;
  }

  public ISOPackager newPackager() {
    try {
      return packagerClass.getDeclaredConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Cannot create packager for " + code, e);
    }
  }

  public static Dialect fromCode(String code) {
    return Arrays.stream(values())
        .filter(d -> d.code.equals(code))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown dialect: " + code));
  }
}
