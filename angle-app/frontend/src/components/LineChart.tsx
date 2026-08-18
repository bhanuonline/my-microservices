import { LineChart as RLineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts'

type Props = { data: Array<{ label: string; value: number }>; color?: string; height?: number }

export default function LineChart({ data, color = '#0E9F6E', height = 240 }: Props) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <RLineChart data={data} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#E1E7F0" />
        <XAxis dataKey="label" stroke="#667085" fontSize={12} />
        <YAxis stroke="#667085" fontSize={12} />
        <Tooltip contentStyle={{ borderRadius: 8, border: '1px solid #E1E7F0', fontSize: 13 }} />
        <Line type="monotone" dataKey="value" stroke={color} strokeWidth={2.4} dot={false} />
      </RLineChart>
    </ResponsiveContainer>
  )
}
