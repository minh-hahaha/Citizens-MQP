/** Where everything lives. These match docker-compose.yml. */
export const config = {
  keycloakAuthority: 'http://localhost:8080/realms/citizens',
  clientId: 'aggregator-ui',
  scope: 'openid fdx:accountbasic:read fdx:paymentsupport:read',
  apiUrl: 'http://localhost:8081',
  paymentsUrl: 'http://localhost:8084',
  paymentAmount: 25,
  bankAppsPage: 'http://localhost:8080/realms/citizens/account/applications',
} as const
