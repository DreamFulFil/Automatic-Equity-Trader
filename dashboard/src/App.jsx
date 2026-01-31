import { useState, useEffect } from 'react'
import axios from 'axios'
import Overview from './components/Overview'
import EquityCurve from './components/EquityCurve'
import Positions from './components/Positions'
import Strategies from './components/Strategies'
import RecentTrades from './components/RecentTrades'
import Alerts from './components/Alerts'
import './App.css'

function App() {
  const [overview, setOverview] = useState(null)
  const [equityCurve, setEquityCurve] = useState([])
  const [positions, setPositions] = useState([])
  const [strategies, setStrategies] = useState([])
  const [trades, setTrades] = useState([])
  const [alerts, setAlerts] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    fetchData()
    const interval = setInterval(fetchData, 30000) // Refresh every 30s
    return () => clearInterval(interval)
  }, [])

  const fetchData = async () => {
    try {
      const [overviewRes, curveRes, positionsRes, strategiesRes, tradesRes, alertsRes] = await Promise.all([
        axios.get('/api/dashboard/overview'),
        axios.get('/api/dashboard/equity-curve?days=30'),
        axios.get('/api/dashboard/positions'),
        axios.get('/api/dashboard/strategies?limit=10'),
        axios.get('/api/dashboard/trades?limit=20'),
        axios.get('/api/dashboard/alerts?limit=20')
      ])

      setOverview(overviewRes.data)
      setEquityCurve(curveRes.data)
      setPositions(positionsRes.data)
      setStrategies(strategiesRes.data)
      setTrades(tradesRes.data)
      setAlerts(alertsRes.data)
      setLoading(false)
      setError(null)
    } catch (err) {
      setError(err.message)
      setLoading(false)
    }
  }

  if (loading) {
    return <div className="loading">Loading dashboard...</div>
  }

  if (error) {
    return <div className="error">Error loading dashboard: {error}</div>
  }

  return (
    <div className="app">
      <header className="header">
        <h1>Automatic Equity Trader Dashboard</h1>
        <div className="last-update">Last updated: {new Date().toLocaleTimeString()}</div>
      </header>

      <main className="main">
        <Overview data={overview} />
        <EquityCurve data={equityCurve} />
        
        <div className="grid-container">
          <Positions data={positions} />
          <Strategies data={strategies} />
        </div>

        <div className="grid-container">
          <RecentTrades data={trades} />
          <Alerts data={alerts} onRefresh={fetchData} />
        </div>
      </main>
    </div>
  )
}

export default App
