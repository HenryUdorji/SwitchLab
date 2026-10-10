package io.switchlab.sw.tm;

import java.io.Serializable;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.transaction.Context;
import org.jpos.transaction.GroupSelector;

public class MtiGroupSelector implements GroupSelector {
  @Override
  public int prepare(long id, Serializable context) {
    return PREPARED | READONLY | NO_JOIN;
  }

  @Override
  public String select(long id, Serializable context) {
    ISOMsg request = ((Context) context).get(CtxKeys.REQUEST);
    try {
      return switch (request.getMTI().substring(0, 3)) {
        case "080" -> "netmgmt";
        default -> "unsupported";
      };
    } catch (ISOException e) {
      return "unsupported";
    }
  }
}
