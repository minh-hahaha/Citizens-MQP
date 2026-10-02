import type { ReactNode } from 'react'

export type StepState = 'locked' | 'active' | 'done'

type StepCardProps = {
  number: number
  title: string
  state: StepState
  children: ReactNode
}

/** One step of the demo flow. A locked step shows its title only. */
export function StepCard({ number, title, state, children }: StepCardProps) {
  return (
    <section className={`step step-${state}`} aria-label={`Step ${number}: ${title}`}>
      <div className="step-number" aria-hidden="true">
        {state === 'done' ? '✓' : number}
      </div>
      <div className="step-body">
        <h2>{title}</h2>
        {state !== 'locked' && children}
      </div>
    </section>
  )
}
