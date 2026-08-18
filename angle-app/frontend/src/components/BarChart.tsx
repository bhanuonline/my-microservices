import { BarChart as RBarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts'

type Props = { data: Array<{ label: string; value: number }>; color?: string; height?: number }

export default function BarChart({ data, color = '#10182B', height = 240 }: Props) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <RBarChart data={data} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#E1E7F0" />
        <XAxis dataKey="label" stroke="#667085" fontSize={12} />
        <YAxis stroke="#667085" fontSize={12} />
        <Tooltip contentStyle={{ borderRadius: 8, border: '1px solid #E1E7F0', fontSize: 13 }} />
        <Bar dataKey="value" fill={color} radius={[4, 4, 0, 0]} />
      </RBarChart>
    </ResponsiveContainer>
  )
}
