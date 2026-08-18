type LoadingProps = { label?: string }

export function Loading({ label = 'Loading…' }: LoadingProps) {
  return (
    <div style={{ padding: 40, textAlign: 'center', color: 'var(--muted)' }}>
      <div
        style={{
          display: 'inline-block',
          width: 22,
          height: 22,
          border: '2.5px solid var(--line)',
          borderTopColor: 'var(--ink)',
          borderRadius: '50%',
          animation: 'spin 0.8s linear infinite',
          marginRight: 10,
          verticalAlign: 'middle'
        }}
      />
      {label}
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  )
}

type ErrorProps = { message: string }

export function ErrorMessage({ message }: ErrorProps) {
  return (
    <div
      style={{
        padding: 20,
        background: 'rgba(228, 72, 58, 0.08)',
        border: '1px solid rgba(228, 72, 58, 0.25)',
        borderRadius: 12,
        color: 'var(--red)',
        fontSize: 14
      }}
    >
      <strong>Something went wrong.</strong>
      <div style={{ marginTop: 6, fontFamily: 'var(--font-mono)', fontSize: 13 }}>{message}</div>
      <div style={{ marginTop: 10, color: 'var(--muted)', fontSize: 13 }}>
        Is the Spring backend running on <span className="mono">localhost:9010</span>?
      </div>
    </div>
  )
}
