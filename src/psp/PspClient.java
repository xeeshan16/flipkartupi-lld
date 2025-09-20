package psp;

import java.math.BigDecimal;

/* --------------------------
   PSP CLIENT STRATEGY (Strategy Pattern)
   -------------------------- */
public interface PspClient {
    enum PspStatus { SUCCESS, PENDING, FAILED }
    class PspResponse {
        public final PspStatus status;
        public final String pspTxnId;
        public final String errorCode;
        public PspResponse(PspStatus status, String pspTxnId, String error) {
            this.status = status; this.pspTxnId = pspTxnId; this.errorCode = error;
        }
    }
    // initiate transfer
    PspResponse initiateTransfer(String fromMasked, String toIdentifier, BigDecimal amount);

    // query status of a previously initiated transfer (for reconciliation)
    PspResponse queryStatus(String pspTxnId);
}