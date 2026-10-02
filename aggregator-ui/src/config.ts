/** Where everything lives. Defaults match infra/docker-compose.yml. */
export const config = {
  keycloakAuthority: import.meta.env.VITE_KEYCLOAK_AUTHORITY ?? 'http://localhost:8080/realms/citizens',
  clientId: import.meta.env.VITE_CLIENT_ID ?? 'aggregator-ui',
  scope: 'openid fdx:accountbasic:read fdx:paymentsupport:read',
  apiUrl: import.meta.env.VITE_API_URL ?? 'http://localhost:8081',
  paymentsUrl: import.meta.env.VITE_PAYMENTS_URL ?? 'http://localhost:8084',
  bankAppsPage:
    import.meta.env.VITE_BANK_APPS_PAGE ?? 'http://localhost:8080/realms/citizens/account/applications',
  paymentAmount: 25,
} as const
