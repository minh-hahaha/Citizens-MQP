import type { RequestLogEntry } from '../api'

/** Every call the aggregator made, with the x-fapi-interaction-id the API echoed back. */
export function RequestLog({ entries }: { entries: RequestLogEntry[] }) {
  if (entries.length === 0) {
    return null
  }
  return (
    <section className="request-log" aria-label="Request log">
      <h2>Request log</h2>
      <table>
        <thead>
          <tr>
            <th>Call</th>
            <th>Status</th>
            <th>x-fapi-interaction-id</th>
          </tr>
        </thead>
        <tbody>
          {entries.map((entry, index) => (
            <tr key={index}>
              <td>
                <code>{entry.call}</code>
              </td>
              <td className={entry.status < 400 ? 'ok' : 'bad'}>{entry.status}</td>
              <td>{entry.interactionId ? <code>{entry.interactionId}</code> : 'not an FDX call'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}
