import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'

function EquityCurve({ data }) {
  if (!data || data.length === 0) return null

  const formatCurrency = (value) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 0
    }).format(value)
  }

  const formatDate = (dateStr) => {
    return new Date(dateStr).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
  }

  return (
    <div className="card" style={{ marginBottom: '1.5rem' }}>
      <div className="card-header">
        <h2 className="card-title">Equity Curve (30 Days)</h2>
      </div>

      <ResponsiveContainer width="100%" height={300}>
        <LineChart data={data}>
          <CartesianGrid strokeDasharray="3 3" stroke="#30363d" />
          <XAxis 
            dataKey="date" 
            tickFormatter={formatDate}
            stroke="#8b949e"
            style={{ fontSize: '0.85rem' }}
          />
          <YAxis 
            tickFormatter={formatCurrency}
            stroke="#8b949e"
            style={{ fontSize: '0.85rem' }}
          />
          <Tooltip 
            formatter={formatCurrency}
            labelFormatter={formatDate}
            contentStyle={{
              backgroundColor: '#161b22',
              border: '1px solid #30363d',
              borderRadius: '6px',
              color: '#e6edf3'
            }}
          />
          <Line 
            type="monotone" 
            dataKey="cumulativePnL" 
            stroke="#58a6ff" 
            strokeWidth={2}
            dot={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}

export default EquityCurve
