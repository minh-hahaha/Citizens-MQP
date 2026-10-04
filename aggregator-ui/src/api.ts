/** Calls to the Open Banking API (through the gateway) and to the Payment Receiver. */
import { config } from './config'

export type Account = {
  accountId: string
  accountType: string
  accountNumberDisplay: string
  nickname: string
}

/** FDX AccountPaymentNetwork: what the aggregator gets instead of the account number. */
export type PaymentNetwork = {
  bankId: string
  identifier: string
  identifierType: string
  type: string
}

export type PaymentResult = {
  paymentId: string
  status: 'POSTED' | 'REJECTED'
  reason: string | null
}

/** One line of the request log shown at the bottom of the page. */
export type RequestLogEntry = {
  call: string
  status: number
  interactionId: string | null
}

/** Called once for every request, so the page can show it in the request log. */
export type LogRequest = (entry: RequestLogEntry) => void

/** Every FDX call carries the access token and a fresh x-fapi-interaction-id. */
async function fdxGet(path: string, accessToken: string, log: LogRequest) {
  const response = await fetch(`${config.apiUrl}${path}`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'x-fapi-interaction-id': crypto.randomUUID(),
    },
  })
  log({
    call: `GET ${path}`,
    status: response.status,
    interactionId: response.headers.get('x-fapi-interaction-id'),
  })
  const body = await response.json()
  if (!response.ok) {
    throw new Error(`FDX error ${body.code}: ${body.message}`)
  }
  return body
}

export async function listAccounts(accessToken: string, log: LogRequest): Promise<Account[]> {
  const body = await fdxGet('/fdx/v6/accounts', accessToken, log)
  return body.accounts
}

export async function getPaymentNetwork(
  accessToken: string,
  accountId: string,
  log: LogRequest,
): Promise<PaymentNetwork> {
  const body = await fdxGet(`/fdx/v6/accounts/${accountId}/payment-networks`, accessToken, log)
  return body.paymentNetworks[0]
}

/** Pays to a routing number and token. A rejected payment is a normal outcome, not an error. */
export async function sendPayment(network: PaymentNetwork, amount: number, log: LogRequest): Promise<PaymentResult> {
  const response = await fetch(`${config.paymentsUrl}/v1/payments`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ routingNumber: network.bankId, accountIdentifier: network.identifier, amount }),
  })
  log({ call: 'POST /v1/payments', status: response.status, interactionId: null })
  const body = await response.json()
  if (body.status !== 'POSTED' && body.status !== 'REJECTED') {
    throw new Error(`Payment request failed (HTTP ${response.status})`)
  }
  return body
}
