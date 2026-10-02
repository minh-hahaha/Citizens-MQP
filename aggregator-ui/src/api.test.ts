import { describe, expect, test, vi } from 'vitest'
import { ApiError, createApi, type RequestLogEntry } from './api'

const API_URL = 'http://gateway.test'
const PAYMENTS_URL = 'http://payments.test'
const INTERACTION_ID = 'c770aef3-6784-41f7-8e0e-ff5f97bddb3a'

function jsonResponse(status: number, body: unknown, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  })
}

function apiWith(response: Response, onRequest?: (entry: RequestLogEntry) => void) {
  const fetchFn = vi.fn<typeof fetch>().mockResolvedValue(response)
  const api = createApi({
    apiUrl: API_URL,
    paymentsUrl: PAYMENTS_URL,
    fetchFn,
    onRequest,
    newInteractionId: () => INTERACTION_ID,
  })
  return { api, fetchFn }
}

describe('listAccounts', () => {
  test('sends the access token and an interaction id, and returns the accounts', async () => {
    const accounts = [{ accountId: 'acc-1001', nickname: 'Everyday Checking' }]
    const { api, fetchFn } = apiWith(jsonResponse(200, { accounts }))

    const result = await api.listAccounts('access-token')

    expect(result).toEqual(accounts)
    expect(fetchFn).toHaveBeenCalledWith(`${API_URL}/fdx/v6/accounts`, {
      method: 'GET',
      headers: { Authorization: 'Bearer access-token', 'x-fapi-interaction-id': INTERACTION_ID },
    })
  })

  test('throws an ApiError carrying the FDX error code and message', async () => {
    const { api } = apiWith(jsonResponse(401, { code: '603', message: 'Authentication failed' }))

    const error = await api.listAccounts('bad-token').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 401, code: '603', message: 'Authentication failed' })
  })

  test('throws when the response has no accounts list', async () => {
    const { api } = apiWith(jsonResponse(200, { unexpected: true }))

    await expect(api.listAccounts('access-token')).rejects.toBeInstanceOf(ApiError)
  })
})

describe('getPaymentNetwork', () => {
  test('returns the first payment network, which carries the token', async () => {
    const network = { bankId: '123456780', identifier: '482910375526', identifierType: 'TOKENIZED_ACCOUNT_NUMBER' }
    const { api, fetchFn } = apiWith(jsonResponse(200, { paymentNetworks: [network] }))

    const result = await api.getPaymentNetwork('access-token', 'acc-1001')

    expect(result).toEqual(network)
    expect(fetchFn.mock.calls[0][0]).toBe(`${API_URL}/fdx/v6/accounts/acc-1001/payment-networks`)
  })

  test('throws when the list is empty', async () => {
    const { api } = apiWith(jsonResponse(200, { paymentNetworks: [] }))

    await expect(api.getPaymentNetwork('access-token', 'acc-1001')).rejects.toBeInstanceOf(ApiError)
  })
})

describe('sendPayment', () => {
  test('returns a posted payment', async () => {
    const { api, fetchFn } = apiWith(jsonResponse(201, { paymentId: 'p-1', status: 'POSTED' }))

    const result = await api.sendPayment('123456780', '482910375526', 25)

    expect(result).toEqual({ paymentId: 'p-1', status: 'POSTED' })
    expect(fetchFn).toHaveBeenCalledWith(`${PAYMENTS_URL}/v1/payments`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ routingNumber: '123456780', accountIdentifier: '482910375526', amount: 25 }),
    })
  })

  test('returns a rejected payment instead of throwing', async () => {
    const rejected = { paymentId: 'p-2', status: 'REJECTED', reason: 'Unable to locate account' }
    const { api } = apiWith(jsonResponse(422, rejected))

    await expect(api.sendPayment('123456780', '482910375526', 25)).resolves.toEqual(rejected)
  })

  test('throws when the receiver answers with something else', async () => {
    const { api } = apiWith(jsonResponse(400, { error: 'Invalid payment request' }))

    await expect(api.sendPayment('1', '2', 25)).rejects.toBeInstanceOf(ApiError)
  })
})

describe('request log', () => {
  test('reports each call with its status and the echoed interaction id', async () => {
    const entries: RequestLogEntry[] = []
    const response = jsonResponse(200, { accounts: [] }, { 'x-fapi-interaction-id': INTERACTION_ID })
    const { api } = apiWith(response, (entry) => entries.push(entry))

    await api.listAccounts('access-token')

    expect(entries).toEqual([
      { method: 'GET', url: `${API_URL}/fdx/v6/accounts`, status: 200, interactionId: INTERACTION_ID },
    ])
  })
})
