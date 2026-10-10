package io.switchlab.sw.participant;

import io.switchlab.sw.tm.CtxKeys;
import java.io.Serializable;
import org.jpos.iso.ISOException;
import org.jpos.transaction.Context;
import org.jpos.transaction.TransactionParticipant;

public class Unsupported implements TransactionParticipant {
  private static final int[] ECHO = {3, 4, 7, 11, 12, 13, 37, 41, 42};

  @Override
  public int prepare(long id, Serializable context) {
    Context ctx = (Context) context;
    try {
      ctx.put(CtxKeys.RESPONSE, Responses.answer(ctx.get(CtxKeys.REQUEST), ECHO, "40"));
      return PREPARED | NO_JOIN;
    } catch (ISOException e) {
      ctx.log(e);
      return ABORTED;
    }
  }
}
