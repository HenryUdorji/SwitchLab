package io.switchlab.sw.participant;

import io.switchlab.sw.tm.CtxKeys;
import java.io.IOException;
import java.io.Serializable;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOSource;
import org.jpos.transaction.AbortParticipant;
import org.jpos.transaction.Context;

public class SendResponse implements AbortParticipant {
  @Override
  public int prepare(long id, Serializable context) {
    return PREPARED | READONLY;
  }

  @Override
  public void commit(long id, Serializable context) {
    send((Context) context);
  }

  @Override
  public void abort(long id, Serializable context) {
    send((Context) context);
  }

  private void send(Context ctx) {
    ISOSource source = ctx.get(CtxKeys.SOURCE);
    ISOMsg response = ctx.get(CtxKeys.RESPONSE);
    if (source == null || response == null || !source.isConnected()) {
      return;
    }
    try {
      source.send(response);
    } catch (IOException | ISOException e) {
      ctx.log(e);
    }
  }
}
