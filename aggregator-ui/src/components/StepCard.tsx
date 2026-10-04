import type { ReactNode } from 'react'

type StepCardProps = {
  number: number
  title: string
  unlocked: boolean
  done: boolean
  children: ReactNode
}

/** One step of the demo flow. A locked step shows its title only. */
export function StepCard({ number, title, unlocked, done, children }: StepCardProps) {
  const state = !unlocked ? 'locked' : done ? 'done' : 'active'
  return (
    <section className={`step step-${state}`}>
      <div className="step-number">{done ? '✓' : number}</div>
      <div className="step-body">
        <h2>{title}</h2>
        {unlocked && children}
      </div>
    </section>
  )
}
