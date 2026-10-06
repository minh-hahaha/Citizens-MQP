import { useState, useEffect } from 'react'
import keycloak from './keycloak'
import './App.css'

function App() {
  const [loading, setLoading] = useState(true)
  const [authenticating, setAuthenticating] = useState(false)

  useEffect(() => {
    keycloak.init({
      onLoad: 'check-sso',
      pkceMethod: 'S256'
    }).then((auth) => {
      setAuthenticating(auth)
      setLoading(false)
    }).catch((err) => {
      console.error("Keycloak error: ", err)
      setLoading(false)
    })
  }, [])

  if (loading) {
    return <div>Loading...</div>
  }

  return (
    <>
    {!authenticating ? (
      <div>
        <p>You are not logged in</p>
        <button onClick={() => keycloak.login()}>Log in</button>
      </div>
    ) : (
      <div>
        <p>You are logged in</p>
      </div>
    )}
    </>
  )
}

export default App
