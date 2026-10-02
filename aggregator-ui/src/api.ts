/** Calls to the Open Banking API (through the gateway) and to the Payment Receiver. */

export const INTERACTION_ID_HEADER = 'x-fapi-interaction-id'

export type Account = {
  accountId: string
  accountType: string
  accountNumberDisplay: string
  nickname: string
  status: string
}

/** FDX AccountPaymentNetwork: what the aggregator gets instead of the account number. */
export type PaymentNetwork = {
  bankId: string
  identifier: string
  identifierType: string
  type: string
  transferIn: boolean
  transferOut: boolean
}

export type PaymentResult = {
  paymentId: string
  status: 'POSTED' | 'REJECTED'
  reason?: string
}

export type LedgerEntry = {
  paymentId: string
  accountRef: string
  amount: number
  postedAt: string
}

/** One line of the request log shown at the bottom of the page. */
export type RequestLogEntry = {
  method: string
  url: string
  status: number
  interactionId: string | null
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string | null

  constructor(status: number, code: string | null, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

export type ApiOptions = {
  apiUrl: string
  paymentsUrl: string
  onRequest?: (entry: RequestLogEntry) => void
  fetchFn?: typeof fetch
  newInteractionId?: () => string
}

export function createApi(options: ApiOptions) {
  const fetchFn = options.fetchFn ?? ((input, init) => fetch(input, init))
  const newInteractionId = options.newInteractionId ?? (() => crypto.randomUUID())

  async function send(method: string, url: string, init: RequestInit): Promise<Response> {
    const response = await fetchFn(url, { ...init, method })
    options.onRequest?.({
      method,
      url,
      status: response.status,
      interactionId: response.headers.get(INTERACTION_ID_HEADER),
    })
    return response
  }

  /** FDX calls carry the access token and a fresh x-fapi-interaction-id. Errors are FDX Error bodies. */
  async function fdxGet(path: string, accessToken: string): Promise<unknown> {
    const response = await send('GET', `${options.apiUrl}${path}`, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
        [INTERACTION_ID_HEADER]: newInteractionId(),
      },
    })
    const body = await readJson(response)
    if (!response.ok) {
      const error = body as { code?: unknown; message?: unknown } | null
      throw new ApiError(
        response.status,
        typeof error?.code === 'string' ? error.code : null,
        typeof error?.message === 'string' ? error.message : `Request failed (HTTP ${response.status})`,
      )
    }
    return body
  }

  return {
    async listAccounts(accessToken: string): Promise<Account[]> {
      const body = (await fdxGet('/fdx/v6/accounts', accessToken)) as { accounts?: Account[] } | null
      if (!Array.isArray(body?.accounts)) {
        throw new ApiError(200, null, 'The accounts response had no accounts list')
      }
      return body.accounts
    },

    async getPaymentNetwork(accessToken: string, accountId: string): Promise<PaymentNetwork> {
      const path = `/fdx/v6/accounts/${encodeURIComponent(accountId)}/payment-networks`
      const body = (await fdxGet(path, accessToken)) as { paymentNetworks?: PaymentNetwork[] } | null
      const network = body?.paymentNetworks?.[0]
      if (!network) {
        throw new ApiError(200, null, 'The response had no payment network')
      }
      return network
    },

    /** A rejected payment is a normal outcome, not an error. */
    async sendPayment(routingNumber: string, accountIdentifier: string, amount: number): Promise<PaymentResult> {
      const response = await send('POST', `${options.paymentsUrl}/v1/payments`, {
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ routingNumber, accountIdentifier, amount }),
      })
      const body = (await readJson(response)) as PaymentResult | null
      if (body?.status !== 'POSTED' && body?.status !== 'REJECTED') {
        throw new ApiError(response.status, null, `Payment request failed (HTTP ${response.status})`)
      }
      return body
    },

    async getLedger(): Promise<LedgerEntry[]> {
      const response = await send('GET', `${options.paymentsUrl}/v1/ledger`, {})
      const body = await readJson(response)
      if (!response.ok || !Array.isArray(body)) {
        throw new ApiError(response.status, null, `Could not read the ledger (HTTP ${response.status})`)
      }
      return body as LedgerEntry[]
    },
  }
}

export type Api = ReturnType<typeof createApi>

async function readJson(response: Response): Promise<unknown> {
  try {
    return await response.json()
  } catch {
    return null
  }
}
