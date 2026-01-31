function RecentTrades({ data }) {
  if (!data || data.length === 0) {
    return (
      <div className="card">
        <div className="card-header">
          <h2 className="card-title">Recent Trades</h2>
        </div>
        <p style={{ color: '#8b949e', textAlign: 'center', padding: '2rem' }}>No recent trades</p>
      </div>
    )
  }

  const formatTime = (timestamp) => {
    return new Date(timestamp).toLocaleTimeString('en-US', { 
      hour: '2-digit', 
      minute: '2-digit' 
    })
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
        <h2 className="card-title">Recent Trades</h2>
      </div>

      <div style={{ overflowX: 'auto' }}>
        <table>
          <thead>
            <tr>
              <th>Time</th>
              <th>Symbol</th>
              <th>Action</th>
              <th>Shares</th>
              <th>Price</th>
              <th>Strategy</th>
              <th>P&L</th>
            </tr>
          </thead>
          <tbody>
            {data.map((trade, index) => (
              <tr key={index}>
                <td style={{ fontSize: '0.85rem', color: '#8b949e' }}>
                  {formatTime(trade.timestamp)}
                </td>
                <td style={{ fontWeight: 600, color: '#58a6ff' }}>{trade.symbol}</td>
                <td>
                  <span className={`badge ${trade.action === 'BUY' ? 'success' : 'error'}`}>
                    {trade.action}
                  </span>
                </td>
                <td>{trade.shares}</td>
                <td>{formatCurrency(trade.price)}</td>
                <td style={{ fontSize: '0.85rem' }}>{trade.strategyName}</td>
                <td className={trade.realizedPnL && trade.realizedPnL !== 0 
                  ? (trade.realizedPnL >= 0 ? 'positive' : 'negative') 
                  : ''}>
                  {trade.realizedPnL && trade.realizedPnL !== 0
                    ? formatCurrency(trade.realizedPnL)
                    : '-'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

export default RecentTrades
