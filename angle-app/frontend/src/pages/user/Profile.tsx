import Card from '../../components/Card'
import { Loading, ErrorMessage } from '../../components/LoadingState'
import { useApi } from '../../hooks/useApi'
import { getProfile } from '../../api/endpoints'

const kycBadge = (s: string) => {
  const cls = s === 'VERIFIED' ? 'badge-green' : s === 'PENDING' ? 'badge-amber' : 'badge-red'
  return <span className={`badge ${cls}`}>{s}</span>
}

const Row = ({ label, children }: { label: string; children: React.ReactNode }) => (
  <div>
    <div className="sub" style={{ marginTop: 0 }}>{label}</div>
    <div style={{ fontWeight: 600, marginTop: 2 }}>{children}</div>
  </div>
)

export default function Profile() {
  const { data: profile, loading, error } = useApi(getProfile)

  if (loading) return <Loading label="Loading profile…" />
  if (error) return <ErrorMessage message={error} />
  if (!profile) return null

  return (
    <div className="grid grid-2" style={{ maxWidth: 900 }}>
      <Card title="Account">
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <Row label="Name">{profile.name}</Row>
          <Row label="Email">{profile.email}</Row>
          <Row label="Member since">{profile.memberSince}</Row>
          <Row label="Tier"><span className="badge badge-muted">{profile.tier}</span></Row>
        </div>
      </Card>

      <Card title="KYC status">
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <Row label="Verification">{kycBadge(profile.kycStatus)}</Row>
          <p style={{ fontSize: 13.5, color: 'var(--muted)', lineHeight: 1.55 }}>
            Your identity has been verified. You have full trading access including options and crypto.
          </p>
          <button className="btn btn-secondary" style={{ alignSelf: 'flex-start' }}>Update documents</button>
        </div>
      </Card>
    </div>
  )
}
