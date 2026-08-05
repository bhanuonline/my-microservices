import { ReactNode } from 'react'

export type Column<T> = {
  key: string
  header: string
  render?: (row: T) => ReactNode
}

type Props<T> = { columns: Column<T>[]; rows: T[] }

export default function DataTable<T extends Record<string, any>>({ columns, rows }: Props<T>) {
  return (
    <table className="dt">
      <thead>
        <tr>
          {columns.map((c) => (
            <th key={c.key}>{c.header}</th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((row, i) => (
          <tr key={i}>
            {columns.map((c) => (
              <td key={c.key}>{c.render ? c.render(row) : String(row[c.key] ?? '')}</td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  )
}
