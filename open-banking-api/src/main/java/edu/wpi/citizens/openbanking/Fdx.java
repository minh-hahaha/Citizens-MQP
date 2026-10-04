package edu.wpi.citizens.openbanking;

import java.util.List;

import org.springframework.http.HttpStatus;

/** The FDX v6.4.1 response shapes this prototype uses. Field names come from the FDX schema. */
public final class Fdx {

    private Fdx() {
    }

    /** Response of GET /accounts. */
    public record Accounts(PageMetadata page, List<AccountDescriptor> accounts) {
    }

    /** accountId is a persistent ID, never the account number. */
    public record AccountDescriptor(String accountCategory, String accountId, String accountType,
                                    String accountNumberDisplay, String nickname, String status) {
    }

    /** Response of GET /accounts/{accountId}/payment-networks. */
    public record AccountPaymentNetworkList(PageMetadata page, List<AccountPaymentNetwork> paymentNetworks) {
    }

    /** identifier carries the token, never the real account number. */
    public record AccountPaymentNetwork(String bankId, String identifier, String identifierType, String type,
                                        boolean transferIn, boolean transferOut) {
    }

    public record PageMetadata(int totalElements) {
    }

    /** FDX Error body. code is a string in the FDX schema. */
    public record Error(String code, String message) {
    }

    /** Thrown by the API code and answered with an FDX Error body. */
    public static class ErrorException extends RuntimeException {

        final HttpStatus status;
        final Error error;

        private ErrorException(HttpStatus status, String code, String message) {
            super(message);
            this.status = status;
            this.error = new Error(code, message);
        }

        public static ErrorException accountNotFound() {
            return new ErrorException(HttpStatus.NOT_FOUND, "701", "Account not found");
        }

        public static ErrorException notAuthorized() {
            return new ErrorException(HttpStatus.FORBIDDEN, "602", "Not authorized");
        }
    }
}
