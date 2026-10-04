import { User, UserManager, WebStorageStateStore } from 'oidc-client-ts'
import { config } from './config'

/** Bank login and consent: OAuth authorization code flow with PKCE against Keycloak. */
const userManager = new UserManager({
  authority: config.keycloakAuthority,
  client_id: config.clientId,
  redirect_uri: `${window.location.origin}/`,
  response_type: 'code',
  scope: config.scope,
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
  automaticSilentRenew: false,
})

let pendingLoad: Promise<User | null> | null = null

/** Sends the customer to the bank to log in and consent. */
export function connectBank(): Promise<void> {
  return userManager.signinRedirect()
}

/**
 * Returns the logged-in customer, finishing the redirect back from the bank if that is
 * what this page load is. The result is remembered because React calls this twice in dev.
 */
export function loadUser(): Promise<User | null> {
  pendingLoad ??= loadUserOnce()
  return pendingLoad
}

async function loadUserOnce(): Promise<User | null> {
  const isRedirectFromBank = new URLSearchParams(window.location.search).has('code')
  if (isRedirectFromBank) {
    const user = await userManager.signinCallback()
    window.history.replaceState({}, document.title, window.location.pathname)
    return user ?? null
  }
  const user = await userManager.getUser()
  return user && !user.expired ? user : null
}

/** Forgets the access token held by this page. It does not revoke consent at the bank. */
export async function forgetUser(): Promise<void> {
  pendingLoad = Promise.resolve(null)
  await userManager.removeUser()
}
