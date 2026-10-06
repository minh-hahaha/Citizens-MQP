import { useState, useEffect, useRef } from 'react'
import keycloak from './keycloak'
import {StepCard} from './StepCard.tsx'

function App() {
  const [loading, setLoading] = useState(true)
  const [authenticating, setAuthenticating] = useState(false)
  const [error, setError] = useState<string | null>(null)

  interface User {
    preferred_username: string;
    scope: string;
  }

  const [user, setUser] = useState<User | null>(null)
  const isInitializing = useRef(false);

  useEffect(() => {
    keycloak.init({
      onLoad:"check-sso",
      pkceMethod:"S256"
    }).then((auth) => {
      setAuthenticating(auth)
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

  
  const startOver = async () => {
    keycloak.logout()
  }

  return (
    <main className="page">
      <header className="page-header">
        <div>
          <h1>The Flow of Tokenized Account Numbers</h1>
          <p>
            A prototype app meant to help us learn the tech stack and demonstrate the flow of a tokenized accoutn number. It links a bank account and only ever receives a token, never the
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
      <button onClick={() => keycloak.logout()}>Log out</button>

    </main>
  )
}

export default App
