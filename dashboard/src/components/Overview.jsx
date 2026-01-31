function Overview({ data }) {
  if (!data) return null

  const formatCurrency = (value) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2
    }).format(value)
  }

  const getValueClass = (value) => {
    if (value > 0) return 'positive'
    if (value < 0) return 'negative'
    return ''
  }

  return (
    <div className="card" style={{ marginBottom: '1.5rem' }}>
      <div className="card-header">
        <h2 className="card-title">Portfolio Overview</h2>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem' }}>
        <div className="metric">
          <div className="metric-label">Daily P&L</div>
          <div className={`metric-value ${getValueClass(data.dailyPnL)}`}>
            {formatCurrency(data.dailyPnL)}
          </div>
        </div>

        <div className="metric">
          <div className="metric-label">Weekly P&L</div>
          <div className={`metric-value ${getValueClass(data.weeklyPnL)}`}>
            {formatCurrency(data.weeklyPnL)}
          </div>
        </div>

        <div className="metric">
          <div className="metric-label">Monthly P&L</div>
          <div className={`metric-value ${getValueClass(data.monthlyPnL)}`}>
            {formatCurrency(data.monthlyPnL)}
          </div>
        </div>

        <div className="metric">
          <div className="metric-label">Total P&L</div>
          <div className={`metric-value ${getValueClass(data.totalPnL)}`}>
            {formatCurrency(data.totalPnL)}
          </div>
        </div>

        <div className="metric">
          <div className="metric-label">Active Positions</div>
          <div className="metric-value">{data.activePositions}</div>
        </div>

        <div className="metric">
          <div className="metric-label">Active Strategies</div>
          <div className="metric-value">{data.activeStrategies}</div>
        </div>

        <div className="metric">
          <div className="metric-label">Trades Today</div>
          <div className="metric-value">{data.tradesToday}</div>
        </div>

        <div className="metric">
          <div className="metric-label">Win Rate</div>
          <div className={`metric-value ${data.winRate > 50 ? 'positive' : data.winRate < 40 ? 'negative' : ''}`}>
            {data.winRate?.toFixed(1)}%
          </div>
        </div>

        <div className="metric">
          <div className="metric-label">Sharpe Ratio</div>
          <div className={`metric-value ${data.sharpeRatio > 1 ? 'positive' : data.sharpeRatio < 0.5 ? 'negative' : ''}`}>
            {data.sharpeRatio?.toFixed(2)}
          </div>
        </div>

        <div className="metric">
          <div className="metric-label">Max Drawdown</div>
          <div className="metric-value negative">
            {data.maxDrawdownPercent?.toFixed(1)}%
          </div>
        </div>
      </div>
    </div>
  )
}

export default Overview
