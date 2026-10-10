package io.switchlab.iso;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.junit.jupiter.api.Test;

/**
 * Expected wire images are assembled by hand from the NIBSS POS field definitions, so a wrong
 * length prefix or field class in nibss-pos.xml fails here rather than at a customer's terminal.
 */
class NibssPackagerTest {
  private static final String PAN = "5399830000000001";
  private static final String TRACK2 = "5399830000000001D2812221000000000";
  private static final String HASH = "A".repeat(64);
  private static final String NAME_LOC =
      String.format("%-40s", "SWITCHLAB TEST MERCHANT    LA LANG");
  private static final String POS_DATA = "510101511344101";

  @Test
  void financialRequestMatchesSpecLayout() throws Exception {
    ISOMsg m = commonFields("0200");
    m.set(28, "C00000000");
    m.set(35, TRACK2);
    m.set(52, ISOUtil.hex2byte("0123456789ABCDEF"));

    String expected =
        "0200"
            + "F23C449128E090000000000000000021"
            + "16"
            + PAN
            + "000000"
            + "000000150000"
            + "1010101010"
            + "000123"
            + "101010"
            + "1010"
            + "2812"
            + "5411"
            + "051"
            + "00"
            + "C00000000"
            + "06"
            + "111129"
            + "33"
            + TRACK2
            + "000000000123"
            + "2039ABCD"
            + "2039LA000000001"
            + NAME_LOC
            + "566"
            + "0123456789ABCDEF"
            + "015"
            + POS_DATA
            + HASH;

    assertWireImage(m, expected);
  }

  @Test
  void reversalAdviceCarriesReasonOriginalDataAndReplacementAmounts() throws Exception {
    ISOMsg m = commonFields("0420");
    String originalData = "0200" + "000122" + "1010101009" + "00000111129" + "00000000000";
    String replacement = "0".repeat(42);
    m.set(56, "4021");
    m.set(90, originalData);
    m.set(95, replacement);

    String expected =
        "0420"
            + "F23C448108E081000000004200000021"
            + "16"
            + PAN
            + "000000"
            + "000000150000"
            + "1010101010"
            + "000123"
            + "101010"
            + "1010"
            + "2812"
            + "5411"
            + "051"
            + "00"
            + "06"
            + "111129"
            + "000000000123"
            + "2039ABCD"
            + "2039LA000000001"
            + NAME_LOC
            + "566"
            + "004"
            + "4021"
            + originalData
            + replacement
            + "015"
            + POS_DATA
            + HASH;

    assertWireImage(m, expected);
  }

  @Test
  void keyDownloadResponseCarriesKeyAndCheckValueAsHex() throws Exception {
    String encryptedKeyAndKcv = "0123456789ABCDEFFEDCBA9876543210" + "A1B2C3" + "0".repeat(58);
    ISOMsg m = new ISOMsg("0810");
    m.set(3, "9A0000");
    m.set(7, "1010101010");
    m.set(11, "000123");
    m.set(39, "00");
    m.set(41, "2039ABCD");
    m.set(53, ISOUtil.hex2byte(encryptedKeyAndKcv));
    m.set(62, "02014" + "20261010101010" + "04002" + "60");

    String expected =
        "0810"
            + "2220000002800804"
            + "9A0000"
            + "1010101010"
            + "000123"
            + "00"
            + "2039ABCD"
            + encryptedKeyAndKcv
            + "026"
            + "0201420261010101010"
            + "0400260";

    assertWireImage(m, expected);
  }

  private static ISOMsg commonFields(String mti) throws Exception {
    ISOMsg m = new ISOMsg(mti);
    m.set(2, PAN);
    m.set(3, "000000");
    m.set(4, "000000150000");
    m.set(7, "1010101010");
    m.set(11, "000123");
    m.set(12, "101010");
    m.set(13, "1010");
    m.set(14, "2812");
    m.set(18, "5411");
    m.set(22, "051");
    m.set(25, "00");
    m.set(32, "111129");
    m.set(37, "000000000123");
    m.set(41, "2039ABCD");
    m.set(42, "2039LA000000001");
    m.set(43, NAME_LOC);
    m.set(49, "566");
    m.set(123, POS_DATA);
    m.set(128, HASH);
    return m;
  }

  private static void assertWireImage(ISOMsg m, String expected) throws Exception {
    NibssPackager packager = new NibssPackager();
    m.setPackager(packager);
    byte[] packed = m.pack();
    assertThat(new String(packed, StandardCharsets.US_ASCII)).isEqualTo(expected);

    ISOMsg unpacked = new ISOMsg();
    unpacked.setPackager(packager);
    unpacked.unpack(expected.getBytes(StandardCharsets.US_ASCII));
    assertThat(unpacked.pack()).isEqualTo(packed);
  }
}
