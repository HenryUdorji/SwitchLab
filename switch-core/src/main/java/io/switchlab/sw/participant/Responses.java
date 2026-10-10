package io.switchlab.sw.participant;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;

final class Responses {
  private Responses() {}

  static ISOMsg answer(ISOMsg request, int[] echoFields, String responseCode) throws ISOException {
    ISOMsg response = (ISOMsg) request.clone(echoFields);
    response.setMTI(request.getMTI());
    response.setResponseMTI();
    response.set(39, responseCode);
    return response;
  }
}
