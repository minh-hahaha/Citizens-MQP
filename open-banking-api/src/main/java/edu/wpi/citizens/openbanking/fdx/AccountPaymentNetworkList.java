package edu.wpi.citizens.openbanking.fdx;

import java.util.List;

/** FDX AccountPaymentNetworkList: response of GET /accounts/{accountId}/payment-networks. */
public record AccountPaymentNetworkList(PageMetadata page, List<AccountPaymentNetwork> paymentNetworks) {
}
