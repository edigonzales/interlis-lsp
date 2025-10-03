package ch.so.agi.lsp.interlis;

import java.util.concurrent.CancellationException;

/**
 * Helper utilities to consistently detect and propagate LSP cancellation signals.
 */
final class CancellationUtil {
    private static final String LSP4J_OPERATION_CANCELED =
            "org.eclipse.lsp4j.jsonrpc.CancelChecker$OperationCanceledException";

    private CancellationUtil() {
    }

    static boolean isCancellation(Throwable ex) {
        while (ex != null) {
            if (ex instanceof CancellationException) {
                return true;
            }
            if (LSP4J_OPERATION_CANCELED.equals(ex.getClass().getName())) {
                return true;
            }
            ex = ex.getCause();
        }
        return false;
    }

    static CancellationException propagateCancellation(Throwable ex) {
        if (ex instanceof CancellationException ce) {
            return ce;
        }
        CancellationException cancellation = new CancellationException(ex != null ? ex.getMessage() : null);
        if (ex != null) {
            try {
                cancellation.initCause(ex);
            } catch (IllegalStateException ignored) {
                // ignore - cause already set
            }
        }
        return cancellation;
    }
}
