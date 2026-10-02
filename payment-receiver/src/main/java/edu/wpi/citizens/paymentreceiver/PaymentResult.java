package edu.wpi.citizens.paymentreceiver;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentResult(UUID paymentId, String status, String reason) {

    static final String POSTED = "POSTED";
    static final String REJECTED = "REJECTED";

    /** The one rejection reason. It must not reveal why the token was refused. */
    static final String REJECT_REASON = "Unable to locate account";

    public static PaymentResult posted(UUID paymentId) {
        return new PaymentResult(paymentId, POSTED, null);
    }

    public static PaymentResult rejected(UUID paymentId) {
        return new PaymentResult(paymentId, REJECTED, REJECT_REASON);
    }

    public boolean isPosted() {
        return POSTED.equals(status);
    }
}
