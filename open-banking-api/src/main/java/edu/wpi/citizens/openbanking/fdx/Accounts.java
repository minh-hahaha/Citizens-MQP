package edu.wpi.citizens.openbanking.fdx;

import java.util.List;

/** FDX Accounts: response of GET /accounts. */
public record Accounts(PageMetadata page, List<AccountDescriptor> accounts) {
}
