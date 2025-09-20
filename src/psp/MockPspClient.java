package psp;

import java.math.BigDecimal;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/*
  MockPSP: keeps internal map pspTxnId -> state so queryStatus can return transitions.
  Behavior: on initiate, randomly return SUCCESS/PENDING/FAILED (configurable probabilities).
  If PENDING, the reconciler can query to get eventual result.
*/
public class MockPspClient implements PspClient {
    private final Random rng = new Random();
    private final ConcurrentMap<String, PspStatus> store = new ConcurrentHashMap<>();
    private final double successProb;
    private final double pendingProb;
    // pending may later become success or failed
    public MockPspClient(double successProb, double pendingProb) {
        if (successProb + pendingProb > 1.0) throw new IllegalArgumentException("probabilities invalid");
        this.successProb = successProb;
        this.pendingProb = pendingProb;
    }

    @Override
    public PspResponse initiateTransfer(String fromMasked, String toIdentifier, BigDecimal amount) {
        int v = rng.nextInt(100);
        String pspId = UUID.randomUUID().toString();
        if (v < (int)(successProb * 100)) {
            store.put(pspId, PspStatus.SUCCESS);
            return new PspResponse(PspStatus.SUCCESS, pspId, null);
        } else if (v < (int)((successProb + pendingProb) * 100)) {
            store.put(pspId, PspStatus.PENDING);
            return new PspResponse(PspStatus.PENDING, pspId, null);
        } else {
            store.put(pspId, PspStatus.FAILED);
            return new PspResponse(PspStatus.FAILED, null, "PSP_FAILURE");
        }
    }

    @Override
    public PspResponse queryStatus(String pspTxnId) {
        PspStatus s = store.get(pspTxnId);
        if (s == null) return new PspResponse(PspStatus.FAILED, pspTxnId, "UNKNOWN");
        // if currently PENDING, randomly finalize to SUCCESS or FAILED (simulate PSP resolving)
        if (s == PspStatus.PENDING) {
            int v = rng.nextInt(100);
            if (v < 60) {
                s = PspStatus.SUCCESS;
            } else {
                s = PspStatus.FAILED;
            }
            store.put(pspTxnId, s);
        }
        return s == PspStatus.SUCCESS ? new PspResponse(PspStatus.SUCCESS, pspTxnId, null)
                : new PspResponse(PspStatus.FAILED, pspTxnId, "PSP_RECONCILED_FAILED");
    }
}