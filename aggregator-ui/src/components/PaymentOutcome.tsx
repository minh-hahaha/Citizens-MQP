import type { PaymentResult } from '../api'

/** Shows whether a payment was posted or rejected. */
export function PaymentOutcome({ result }: { result: PaymentResult }) {
  const posted = result.status === 'POSTED'
  return (
    <div className={`outcome ${posted ? 'outcome-posted' : 'outcome-rejected'}`} role="status">
      <strong>{posted ? 'Payment posted' : 'Payment rejected'}</strong>
      <span>
        {posted ? 'The bank resolved the token and credited the account.' : `Reason from the bank: ${result.reason}`}
      </span>
      <code>payment {result.paymentId}</code>
    </div>
  )
}
