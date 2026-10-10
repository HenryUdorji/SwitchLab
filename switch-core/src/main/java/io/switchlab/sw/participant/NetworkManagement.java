package io.switchlab.sw.participant;

import io.switchlab.iso.Dialect;
import io.switchlab.sw.tm.CtxKeys;
import java.io.Serializable;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.transaction.Context;
import org.jpos.transaction.TransactionParticipant;

public class NetworkManagement implements TransactionParticipant {
  private static final int[] NIBSS_ECHO = {7, 11, 12, 13, 41};
  private static final int[] GENERIC_ECHO = {7, 11, 41, 70};
  private static final String NIBSS_CALL_HOME = "9D";
  private static final String GENERIC_ECHO_TEST = "301";

  @Override
  public int prepare(long id, Serializable context) {
    Context ctx = (Context) context;
    ISOMsg request = ctx.get(CtxKeys.REQUEST);
    Dialect dialect = ctx.get(CtxKeys.DIALECT);
    try {
      ISOMsg response =
          switch (dialect) {
            case NIBSS ->
                Responses.answer(request, NIBSS_ECHO, isNibssCallHome(request) ? "00" : "40");
            case GENERIC87 ->
                Responses.answer(
                    request,
                    GENERIC_ECHO,
                    GENERIC_ECHO_TEST.equals(request.getString(70)) ? "00" : "40");
          };
      ctx.put(CtxKeys.RESPONSE, response);
      return PREPARED | NO_JOIN;
    } catch (ISOException e) {
      ctx.log(e);
      return ABORTED;
    }
  }

  private static boolean isNibssCallHome(ISOMsg request) {
    String processingCode = request.getString(3);
    return processingCode != null && processingCode.startsWith(NIBSS_CALL_HOME);
  }
}
