import type { ReactNode } from 'react'
import { Tooltip } from 'react-tooltip'

type StepCardProps = {
  number: number
  title: string
  tooltip: string
  unlocked: boolean
  done: boolean
  children: ReactNode
}

export function StepCard({ number, title, tooltip, unlocked, done, children }: StepCardProps) {
  const state = !unlocked ? 'locked' : done ? 'done' : 'active'
  return (
    <section className={`step step-${state}`}>
      <div className="step-number">{done ? '✓' : number}</div>
      <div className="step-body">
        <h2>{title} <span className="tooltip-trigger-circle" data-tooltip-id="tooltip" data-tooltip-content={tooltip}>?</span></h2>
        <Tooltip id="tooltip"></Tooltip>
        {unlocked && children}
      </div>
    </section>
  )
}
