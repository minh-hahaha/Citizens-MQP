import { useCallback, useEffect, useMemo, useState } from 'react'
import type { User } from 'oidc-client-ts'
import {
  ApiError,
  createApi,
  type Account,
  type LedgerEntry,
  type PaymentNetwork,
  type PaymentResult,
  type RequestLogEntry,
} from './api'
import { connectBank, forgetUser, loadUser } from './auth'
import { config } from './config'
import { PaymentOutcome } from './components/PaymentOutcome'
import { RequestLog } from './components/RequestLog'
import { StepCard, type StepState } from './components/StepCard'

function describe(error: unknown): string {
  if (error instanceof ApiError) {
    return error.code ? `FDX error ${error.code}: ${error.message}` : error.message
  }
  return error instanceof Error ? error.message : 'Something went wrong'
}

function stepState(unlocked: boolean, done: boolean): StepState {
  if (!unlocked) return 'locked'
  return done ? 'done' : 'active'
}

export function App() {
  const [user, setUser] = useState<User | null>(null)
  const [accounts, setAccounts] = useState<Account[] | null>(null)
  const [accountId, setAccountId] = useState<string | null>(null)
  const [network, setNetwork] = useState<PaymentNetwork | null>(null)
  const [payments, setPayments] = useState<PaymentResult[]>([])
  const [ledger, setLedger] = useState<LedgerEntry[]>([])
  const [visitedBank, setVisitedBank] = useState(false)
  const [log, setLog] = useState<RequestLogEntry[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const api = useMemo(
    () =>
      createApi({
        apiUrl: config.apiUrl,
        paymentsUrl: config.paymentsUrl,
        onRequest: (entry) => setLog((entries) => [...entries, entry]),
      }),
    [],
  )

  /** Runs one action, showing a busy state and any error it produces. */
  const run = useCallback(async (action: () => Promise<void>) => {
    setBusy(true)
    setError(null)
    try {
      await action()
    } catch (e) {
      setError(describe(e))
    } finally {
      setBusy(false)
    }
  }, [])

  useEffect(() => {
    void run(async () => {
      const loaded = await loadUser()
      setUser(loaded)
      if (loaded) {
        setAccounts(await api.listAccounts(loaded.access_token))
      }
    })
  }, [api, run])

  const getToken = () =>
    run(async () => {
      if (!user || !accountId) return
      setNetwork(await api.getPaymentNetwork(user.access_token, accountId))
      setPayments([])
    })

  const sendPayment = () =>
    run(async () => {
      if (!network) return
      const result = await api.sendPayment(network.bankId, network.identifier, config.paymentAmount)
      setPayments((previous) => [...previous, result])
      setLedger(await api.getLedger())
    })

  const startOver = () =>
    run(async () => {
      await forgetUser()
      setUser(null)
      setAccounts(null)
      setAccountId(null)
      setNetwork(null)
      setPayments([])
      setVisitedBank(false)
      setLog([])
    })

  const firstPayment = payments[0]
  const laterPayments = payments.slice(1)
  const firstPosted = firstPayment?.status === 'POSTED'
  const myLedger = ledger.filter((entry) => payments.some((payment) => payment.paymentId === entry.paymentId))
  const amount = `$${config.paymentAmount.toFixed(2)}`

  return (
    <main className="page">
      <header className="page-header">
        <div>
          <h1>Mock Aggregator</h1>
          <p>
            A stand-in for a data aggregator. It links a bank account and only ever receives a token, never the
            account number. All data is fake.
          </p>
        </div>
        {user && (
          <button className="secondary" onClick={startOver} disabled={busy}>
            Start over
          </button>
        )}
      </header>

      {error && (
        <div className="error" role="alert">
          {error}
        </div>
      )}

      <StepCard number={1} title="Log in at the bank and consent" state={stepState(true, user !== null)}>
        {user ? (
          <p>
            Connected as <strong>{user.profile.preferred_username}</strong>. The bank issued this aggregator an
            access token limited to: <code>{user.scope}</code>
          </p>
        ) : (
          <>
            <p>The bank asks the customer to log in and to agree to what the aggregator may see.</p>
            <button onClick={() => run(connectBank)} disabled={busy}>
              Connect your bank
            </button>
            <p className="hint">Fake customers: alice, bob or carol. Password: password.</p>
          </>
        )}
      </StepCard>

      <StepCard number={2} title="Pick an account" state={stepState(accounts !== null, accountId !== null)}>
        <p>
          From <code>GET /fdx/v6/accounts</code>. The aggregator sees a masked number only.
        </p>
        <div className="accounts">
          {accounts?.map((account) => (
            <label key={account.accountId} className={account.accountId === accountId ? 'account selected' : 'account'}>
              <input
                type="radio"
                name="account"
                checked={account.accountId === accountId}
                onChange={() => {
                  setAccountId(account.accountId)
                  setNetwork(null)
                  setPayments([])
                }}
              />
              <span className="account-name">{account.nickname}</span>
              <span className="account-detail">
                {account.accountType} {account.accountNumberDisplay}
              </span>
            </label>
          ))}
        </div>
      </StepCard>

      <StepCard number={3} title="Get payment details" state={stepState(accountId !== null, network !== null)}>
        <p>
          From <code>GET /fdx/v6/accounts/{accountId ?? '{accountId}'}/payment-networks</code>.
        </p>
        {network ? (
          <div className="stored">
            <h3>What the aggregator stores</h3>
            <dl>
              <dt>Routing number (bankId)</dt>
              <dd>{network.bankId}</dd>
              <dt>Account identifier</dt>
              <dd className="token">{network.identifier}</dd>
              <dt>identifierType</dt>
              <dd>{network.identifierType}</dd>
              <dt>Network</dt>
              <dd>{network.type}</dd>
            </dl>
            <p className="hint">
              This is a token. The real account number never left the bank, and the token only works while the
              customer's consent stands.
            </p>
          </div>
        ) : (
          <button onClick={getToken} disabled={busy}>
            Get payment details
          </button>
        )}
      </StepCard>

      <StepCard number={4} title="Send a test payment" state={stepState(network !== null, firstPayment !== undefined)}>
        <p>The aggregator pays {amount} to the routing number and token, the way an ACH payment is addressed.</p>
        {firstPayment ? (
          <PaymentOutcome result={firstPayment} />
        ) : (
          <button onClick={sendPayment} disabled={busy}>
            Send {amount}
          </button>
        )}
        {myLedger.length > 0 && (
          <p className="hint">
            Bank ledger: {myLedger.length} payment{myLedger.length === 1 ? '' : 's'} posted to this account, total $
            {myLedger.reduce((sum, entry) => sum + entry.amount, 0).toFixed(2)}.
          </p>
        )}
      </StepCard>

      <StepCard number={5} title="Revoke consent at the bank" state={stepState(firstPosted, visitedBank)}>
        <p>
          The customer changes their mind. On the bank's page, open <strong>Mock Aggregator</strong> and choose{' '}
          <strong>Remove access</strong>.
        </p>
        <a
          className="button secondary"
          href={config.bankAppsPage}
          target="_blank"
          rel="noreferrer"
          onClick={() => setVisitedBank(true)}
        >
          Open the bank's connected apps page
        </a>
        <p className="hint">The bank picks up the change within a few seconds and revokes the token.</p>
      </StepCard>

      <StepCard number={6} title="Send the same payment again" state={stepState(visitedBank, laterPayments.length > 0)}>
        <p>Same routing number, same token, same amount.</p>
        <button onClick={sendPayment} disabled={busy}>
          Send {amount} again
        </button>
        {laterPayments.map((payment) => (
          <PaymentOutcome key={payment.paymentId} result={payment} />
        ))}
      </StepCard>

      <RequestLog entries={log} />
    </main>
  )
}
