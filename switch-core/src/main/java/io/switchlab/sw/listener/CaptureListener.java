package io.switchlab.sw.listener;

import io.switchlab.iso.Dialect;
import io.switchlab.sw.tm.CtxKeys;
import java.time.Instant;
import org.jpos.core.Configurable;
import org.jpos.core.Configuration;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISORequestListener;
import org.jpos.iso.ISOSource;
import org.jpos.space.Space;
import org.jpos.space.SpaceFactory;
import org.jpos.transaction.Context;

public class CaptureListener implements ISORequestListener, Configurable {
  @SuppressWarnings("unchecked")
  private final Space<String, Context> space = SpaceFactory.getSpace();

  private String queue;
  private Dialect dialect;

  @Override
  public void setConfiguration(Configuration cfg) {
    queue = cfg.get("queue", "txnmgr");
    dialect = Dialect.fromCode(cfg.get("dialect"));
  }

  @Override
  public boolean process(ISOSource source, ISOMsg m) {
    Context ctx = new Context();
    ctx.put(CtxKeys.RECEIVED_AT, Instant.now());
    ctx.put(CtxKeys.REQUEST, m);
    ctx.put(CtxKeys.SOURCE, source, false);
    ctx.put(CtxKeys.DIALECT, dialect);
    space.out(queue, ctx);
    return true;
  }
}
