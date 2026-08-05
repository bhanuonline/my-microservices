import { ReactNode } from 'react'

type Props = { title?: string; children: ReactNode; actions?: ReactNode }

export default function Card({ title, children, actions }: Props) {
  return (
    <div className="card">
      {(title || actions) && (
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
          {title && <div style={{ fontSize: 14, fontWeight: 600 }}>{title}</div>}
          {actions}
        </div>
      )}
      {children}
    </div>
  )
}
