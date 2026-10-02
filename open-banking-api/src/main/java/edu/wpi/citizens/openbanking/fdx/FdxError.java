package edu.wpi.citizens.openbanking.fdx;

import com.fasterxml.jackson.annotation.JsonInclude;

/** FDX Error body. code is a string in the FDX schema. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FdxError(String code, String message, String debugMessage) {

    public static final FdxError INVALID_INPUT = new FdxError("401", "Invalid input", null);
    public static final FdxError NOT_AUTHORIZED = new FdxError("602", "Not authorized", null);
    public static final FdxError AUTHENTICATION_FAILED = new FdxError("603", "Authentication failed", null);
    public static final FdxError ACCOUNT_NOT_FOUND = new FdxError("701", "Account not found", null);
    public static final FdxError INTERNAL_ERROR = new FdxError("500", "Internal server error", null);

    public FdxError withDebugMessage(String debug) {
        return new FdxError(code, message, debug);
    }
}
