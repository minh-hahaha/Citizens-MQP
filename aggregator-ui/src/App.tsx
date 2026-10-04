import { useEffect, useState } from 'react'
import type { User } from 'oidc-client-ts'
import {
  getPaymentNetwork,
  listAccounts,
  sendPayment,
  type Account,
  type PaymentNetwork,
  type PaymentResult,
  type RequestLogEntry,
} from './api'
import { connectBank, forgetUser, loadUser } from './auth'
import { config } from './config'
import { PaymentOutcome } from './components/PaymentOutcome'
import { RequestLog } from './components/RequestLog'
import { StepCard } from './components/StepCard'

export function App() {
  const [user, setUser] = useState<User | null>(null)
  const [accounts, setAccounts] = useState<Account[]>([])
  const [accountId, setAccountId] = useState<string | null>(null)
  const [network, setNetwork] = useState<PaymentNetwork | null>(null)
  const [payments, setPayments] = useState<PaymentResult[]>([])
  const [visitedBank, setVisitedBank] = useState(false)
  const [requestLog, setRequestLog] = useState<RequestLogEntry[]>([])
  const [error, setError] = useState<string | null>(null)

  const logRequest = (entry: RequestLogEntry) => setRequestLog((entries) => [...entries, entry])

  /** Runs one action and shows any error it produces. */
  const run = async (action: () => Promise<void>) => {
    setError(null)
    try {
      await action()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong')
    }
  }

  // On page load: pick up the login, including coming back from the bank, and list the accounts.
  useEffect(() => {
    void run(async () => {
      const loaded = await loadUser()
      if (!loaded) return
      setUser(loaded)
      setAccounts(await listAccounts(loaded.access_token, logRequest))
    })
  }, [])

  const getAccountNumber = () =>
    run(async () => {
      setNetwork(await getPaymentNetwork(user!.access_token, accountId!, logRequest))
      setPayments([])
      setVisitedBank(false)
    })

  const pay = () =>
    run(async () => {
      const result = await sendPayment(network!, config.paymentAmount, logRequest)
      setPayments((previous) => [...previous, result])
    })

  const startOver = () =>
    run(async () => {
      await forgetUser()
      setUser(null)
      setAccounts([])
      setAccountId(null)
      setNetwork(null)
      setPayments([])
      setVisitedBank(false)
      setRequestLog([])
    })

  const firstPayment = payments[0]
  const laterPayments = payments.slice(1)
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
          <button className="secondary" onClick={startOver}>
            Start over
          </button>
        )}
      </header>

      {error && (
        <div className="error" role="alert">
          {error}
        </div>
      )}

      <StepCard number={1} title="Log in at the bank and consent" unlocked done={user !== null}>
        {user ? (
          <p>
            Connected as <strong>{user.profile.preferred_username}</strong>. The bank issued this aggregator an
            access token limited to: <code>{user.scope}</code>
          </p>
        ) : (
          <>
            <p>The bank asks the customer to log in and to agree to what the aggregator may see.</p>
            <button onClick={() => run(connectBank)}>Connect your bank</button>
            <p className="hint">Fake customer: alice. Password: password.</p>
          </>
        )}
      </StepCard>

      <StepCard number={2} title="Pick an account" unlocked={user !== null} done={accountId !== null}>
        <p>
          From <code>GET /fdx/v6/accounts</code>. The aggregator sees a masked number only.
        </p>
        <div className="accounts">
          {accounts.map((account) => (
            <label key={account.accountId} className={account.accountId === accountId ? 'account selected' : 'account'}>
              <input
                type="radio"
                name="account"
                checked={account.accountId === accountId}
                onChange={() => {
                  setAccountId(account.accountId)
                  setNetwork(null)
                  setPayments([])
                  setVisitedBank(false)
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

      <StepCard number={3} title="Get the account number" unlocked={accountId !== null} done={network !== null}>
        <p>
          From <code>GET /fdx/v6/accounts/{accountId ?? '{accountId}'}/payment-networks</code>.
        </p>
        <button onClick={getAccountNumber}>Get the account number</button>
        {network && (
          <div className="stored">
            <h3>What the aggregator gets</h3>
            <dl>
              <dt>Routing number (bankId)</dt>
              <dd>{network.bankId}</dd>
              <dt>Account number (identifier)</dt>
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
        )}
      </StepCard>

      <StepCard number={4} title="Send a test payment" unlocked={network !== null} done={firstPayment !== undefined}>
        <p>The aggregator pays {amount} to the routing number and token, the way an ACH payment is addressed.</p>
        {firstPayment ? <PaymentOutcome result={firstPayment} /> : <button onClick={pay}>Send {amount}</button>}
      </StepCard>

      <StepCard
        number={5}
        title="Revoke consent at the bank"
        unlocked={firstPayment?.status === 'POSTED'}
        done={visitedBank}
      >
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

      <StepCard number={6} title="Send the same payment again" unlocked={visitedBank} done={laterPayments.length > 0}>
        <p>Same routing number, same token, same amount.</p>
        <button onClick={pay}>Send {amount} again</button>
        {laterPayments.map((payment) => (
          <PaymentOutcome key={payment.paymentId} result={payment} />
        ))}
      </StepCard>

      <RequestLog entries={requestLog} />
    </main>
  )
}
