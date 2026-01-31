function Positions({ data }) {
  if (!data || data.length === 0) {
    return (
      <div className="card">
        <div className="card-header">
          <h2 className="card-title">Active Positions</h2>
        </div>
        <p style={{ color: '#8b949e', textAlign: 'center', padding: '2rem' }}>No active positions</p>
      </div>
    )
  }

  const formatCurrency = (value) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2
    }).format(value)
  }

  return (
    <div className="card">
      <div className="card-header">
        <h2 className="card-title">Active Positions ({data.length})</h2>
      </div>

      <div style={{ overflowX: 'auto' }}>
        <table>
          <thead>
            <tr>
              <th>Symbol</th>
              <th>Shares</th>
              <th>Entry Price</th>
              <th>Current Price</th>
              <th>Unrealized P&L</th>
            </tr>
          </thead>
          <tbody>
            {data.map((position, index) => (
              <tr key={index}>
                <td style={{ fontWeight: 600, color: '#58a6ff' }}>{position.symbol}</td>
                <td>{position.shares}</td>
                <td>{formatCurrency(position.entryPrice)}</td>
                <td>{formatCurrency(position.currentPrice)}</td>
                <td className={position.unrealizedPnL >= 0 ? 'positive' : 'negative'}>
                  {formatCurrency(position.unrealizedPnL)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

export default Positions
