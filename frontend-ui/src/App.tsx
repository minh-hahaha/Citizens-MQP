import { useState, useEffect, useRef } from 'react'
import keycloak from './keycloak'
import {StepCard} from './StepCard.tsx'
import { Tooltip } from 'react-tooltip'

// The gateway is the only address the aggregator knows for the bank's API
const GATEWAY_URL = 'http://localhost:8081'

function App() {
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  interface User {
    preferred_username: string;
    scope: string;
  }

  const [user, setUser] = useState<User | null>(null)
  const isInitializing = useRef(false);

  const [visitedBank, setVisitedBank] = useState(false)

  interface Account {
    accountId: string;
    nickname: string;
    accountNumberDisplay: string;
  }

  // What the aggregator gets in place of the account number
  interface Linked {
    accountId: string;
    bankId: string;
    identifier: string;
    identifierType: string;
  }

  // One line of the request log shown at the bottom of the page
  interface LogEntry {
    call: string;
    status: number;
    ok: boolean;
  }

  const [accounts, setAccounts] = useState<Account[]>([])
  const [linked, setLinked] = useState<Linked | null>(null)
  const [retryResult, setRetryResult] = useState<string | null>(null)
  const [requestLog, setRequestLog] = useState<LogEntry[]>([])

  useEffect(() => {
    if (isInitializing.current) return
    isInitializing.current = true
    keycloak.init({
      onLoad:"check-sso",
      pkceMethod:"S256"
    }).then((auth) => {
      setLoading(false)
      if (auth) {
        const idToken = keycloak.idTokenParsed
        const accessToken = keycloak.tokenParsed
        setUser({
          preferred_username: idToken?.preferred_username || "",
          scope: accessToken?.scope || ""
        })
      }
      else {
        setUser(null)
      }
    }).catch((err) => {
      console.error("Keycloak error: ", err)
      setLoading(false)
    })
  }, [])

  
  // Every call to the bank goes through here: it attaches the access token and logs the call.
  // Returns null if the bank refuses.
  const callBank = async (path: string) => {
    setError(null)
    try {
      const response = await fetch(`${GATEWAY_URL}${path}`, {
        headers: { Authorization: `Bearer ${keycloak.token}` },
      })
      setRequestLog((log) => [...log, { call: `GET ${path}`, status: response.status, ok: response.ok }])
      return response.ok ? await response.json() : null
    } catch (err) {
      console.error("Gateway error: ", err)
      setError("Could not reach the bank's gateway. Is it running?")
      return null
    }
  }

  const listAccounts = async () => {
    const body = await callBank('/fdx/v6/accounts')
    if (body) setAccounts(body.accounts)
  }

  const getAccountNumber = async (accountId: string) => {
    const body = await callBank(`/fdx/v6/accounts/${accountId}/payment-networks`)
    if (body) setLinked({ accountId, ...body.paymentNetworks[0] })
  }

  const tryAgain = async () => {
    const body = await callBank(`/fdx/v6/accounts/${linked!.accountId}/payment-networks`)
    setRetryResult(
      body
        ? 'Still allowed. Was access removed at the bank?'
        : 'Refused. The consent is gone, so the bank revoked the token.'
    )
  }

  const startOver = async () => {
    keycloak.logout()
  }

  return (
    <main className="page">
      <header className="page-header">
        <div>
          <h1>The Flow of Tokenized Account Numbers <span className="tooltip-trigger-circle" data-tooltip-id="react-tooltip" data-tooltip-content="We used React as our front-end framework paired with Typescript">?</span></h1>
          <p>
            A prototype app meant to help us learn the tech stack and demonstrate the flow of a tokenized account number. It links a bank account and only ever receives a token, never the
            account number. All data is fake.
          </p>
          <Tooltip id="react-tooltip"></Tooltip>
        </div>
        {user && (
          <button className="secondary" onClick={startOver}>
            Start over
          </button>
        )}
      </header>

      {loading && <p className="hint">Checking whether you are already logged in...</p>}

      {error && (
        <div className="error" role="alert">
          {error}
        </div>
      )}

      <StepCard number={1} title="Log in at the bank and consent" tooltip="We used Keycloak as our identity access management software" unlocked done={user !== null}>
        {user ? (
          <p>
            Connected as <strong>{user.preferred_username}</strong>. The bank issued this aggregator an
            access token limited to: <code>{user.scope}</code>
          </p>
        ) : (
          <>
            <p>The bank asks the customer to log in and to agree to what the aggregator may see.</p>
            <button onClick={() => keycloak.login()}>Connect your bank</button>
            <p className="hint">Fake customer: alice. Password: password.</p>
          </>
        )}
      </StepCard>
      <StepCard number={2} title="List the accounts" tooltip="We used Spring Cloud Gateway to restrict access to the API calls" unlocked={user !== null} done={accounts.length > 0}>
        <p>
          <code>GET /fdx/v6/accounts</code>. The aggregator sees masked numbers only.
        </p>
        <button onClick={listAccounts}>List accounts</button>
        <div className="accounts">
          {accounts.map((account) => (
            <p key={account.accountId}>
              {account.nickname} {account.accountNumberDisplay}
            </p>
          ))}
        </div>
      </StepCard>

      <StepCard number={3} title="Get the account number" tooltip="We used the FDX API specification in designing our API calls" unlocked={accounts.length > 0} done={linked !== null}>
        <p>
          <code>GET /fdx/v6/accounts/&#123;accountId&#125;/payment-networks</code>. Pick an account:
        </p>
        <div className="accounts">
          {accounts.map((account) => (
            <button key={account.accountId} className="secondary" onClick={() => getAccountNumber(account.accountId)}>
              {account.nickname}
            </button>
          ))}
        </div>
        {linked && (
          <div className="stored">
            <h3>What the aggregator gets</h3>
            <dl>
              <dt>Routing number (bankId)</dt>
              <dd>{linked.bankId}</dd>
              <dt>Account number (identifier)</dt>
              <dd className="token">{linked.identifier}</dd>
              <dt>identifierType</dt>
              <dd>{linked.identifierType}</dd>
            </dl>
            <p className="hint">This is a token. The real account number never left the bank.</p>
          </div>
        )}
      </StepCard>

      <StepCard
        number={4}
        title="Revoke consent at the bank"
        tooltip="Keycloak was a substitute for the IAM software Citizens uses called Ping Identity"
        unlocked={linked !== null}
        done={visitedBank}
      >
        <p>
          The customer changes their mind. On the bank's page, open <strong>Mock Aggregator</strong> and choose{' '}
          <strong>Remove access</strong>.
        </p>
        <a className="button secondary" href="http://localhost:8080/realms/prototype-app/account/applications" target="_blank" rel="noreferrer" onClick={() => setVisitedBank(true)}>
          Open application's page
        </a>
        <p className="hint">The bank notices on the aggregator's next call and revokes the token then.</p>
      </StepCard>

      <StepCard number={5} title="Ask for the account number again" tooltip="We used Docker to package everything together into 1 software container" unlocked={visitedBank} done={retryResult !== null}>
        <p>Same call as step 3, with the same access token.</p>
        <button onClick={tryAgain}>Try again</button>
        {retryResult && <p>{retryResult}</p>}
      </StepCard>

      <section className="request-log">
        <h2>Request log</h2>
        <p className="hint">Every call this page makes to the bank's gateway.</p>
        <table>
          <tbody>
            {requestLog.map((entry, index) => (
              <tr key={index}>
                <td>{entry.call}</td>
                <td className={entry.ok ? 'ok' : 'bad'}>{entry.status}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

    </main>
  )
}

export default App
