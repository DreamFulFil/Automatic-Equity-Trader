function Strategies({ data }) {
  if (!data || data.length === 0) {
    return (
      <div className="card">
        <div className="card-header">
          <h2 className="card-title">Top Strategies</h2>
        </div>
        <p style={{ color: '#8b949e', textAlign: 'center', padding: '2rem' }}>No strategy data available</p>
      </div>
    )
  }

  const formatCurrency = (value) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 0
    }).format(value)
  }

  return (
    <div className="card">
      <div className="card-header">
        <h2 className="card-title">Top Strategies (30-Day Performance)</h2>
      </div>

      <div style={{ overflowX: 'auto' }}>
        <table>
          <thead>
            <tr>
              <th>Strategy</th>
              <th>Trades</th>
              <th>Win Rate</th>
              <th>P&L (30d)</th>
            </tr>
          </thead>
          <tbody>
            {data.map((strategy, index) => (
              <tr key={index}>
                <td style={{ fontWeight: 600, color: '#e6edf3' }}>{strategy.strategyName}</td>
                <td>{strategy.tradeCount}</td>
                <td className={strategy.winRate >= 50 ? 'positive' : 'negative'}>
                  {strategy.winRate?.toFixed(1)}%
                </td>
                <td className={strategy.totalPnL >= 0 ? 'positive' : 'negative'}>
                  {formatCurrency(strategy.totalPnL)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

export default Strategies
