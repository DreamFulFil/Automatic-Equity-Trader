import { useState } from 'react'
import axios from 'axios'

function Alerts({ data, onRefresh }) {
  const [filter, setFilter] = useState('all')

  const handleMarkAllRead = async () => {
    try {
      await axios.post('/api/dashboard/alerts/read-all')
      onRefresh()
    } catch (err) {
      console.error('Error marking alerts as read:', err)
    }
  }

  const handleMarkRead = async (alertId) => {
    try {
      await axios.post(`/api/dashboard/alerts/${alertId}/read`)
      onRefresh()
    } catch (err) {
      console.error('Error marking alert as read:', err)
    }
  }

  const formatTime = (timestamp) => {
    const date = new Date(timestamp)
    const now = new Date()
    const diff = now - date

    if (diff < 60000) return 'Just now'
    if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`
    if (diff < 86400000) return `${Math.floor(diff / 3600000)}h ago`
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
  }

  const getLevelBadgeClass = (level) => {
    switch (level) {
      case 'CRITICAL': return 'error'
      case 'WARNING': return 'warning'
      case 'INFO': return 'info'
      case 'LOW': return 'low'
      default: return 'low'
    }
  }

  const filteredAlerts = filter === 'all' 
    ? data 
    : data.filter(alert => !alert.read)

  return (
    <div className="card">
      <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2 className="card-title">Alerts</h2>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <button 
            onClick={() => setFilter(filter === 'all' ? 'unread' : 'all')}
            style={{
              padding: '0.4rem 0.8rem',
              backgroundColor: '#21262d',
              color: '#e6edf3',
              border: '1px solid #30363d',
              borderRadius: '4px',
              cursor: 'pointer',
              fontSize: '0.85rem'
            }}
          >
            {filter === 'all' ? 'Show Unread' : 'Show All'}
          </button>
          <button 
            onClick={handleMarkAllRead}
            style={{
              padding: '0.4rem 0.8rem',
              backgroundColor: '#21262d',
              color: '#e6edf3',
              border: '1px solid #30363d',
              borderRadius: '4px',
              cursor: 'pointer',
              fontSize: '0.85rem'
            }}
          >
            Mark All Read
          </button>
        </div>
      </div>

      <div style={{ maxHeight: '400px', overflowY: 'auto' }}>
        {filteredAlerts.length === 0 ? (
          <p style={{ color: '#8b949e', textAlign: 'center', padding: '2rem' }}>
            No {filter === 'unread' ? 'unread ' : ''}alerts
          </p>
        ) : (
          filteredAlerts.map((alert, index) => (
            <div 
              key={index}
              style={{
                padding: '1rem',
                borderBottom: index < filteredAlerts.length - 1 ? '1px solid #30363d' : 'none',
                backgroundColor: !alert.read ? '#0d1117' : 'transparent',
                cursor: 'pointer'
              }}
              onClick={() => !alert.read && handleMarkRead(alert.id)}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start', marginBottom: '0.5rem' }}>
                <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                  <span className={`badge ${getLevelBadgeClass(alert.level)}`}>
                    {alert.level}
                  </span>
                  {!alert.read && (
                    <span style={{ 
                      width: '8px', 
                      height: '8px', 
                      backgroundColor: '#58a6ff', 
                      borderRadius: '50%' 
                    }} />
                  )}
                </div>
                <span style={{ fontSize: '0.75rem', color: '#8b949e' }}>
                  {formatTime(alert.timestamp)}
                </span>
              </div>
              <div style={{ fontWeight: 600, marginBottom: '0.25rem' }}>
                {alert.title}
              </div>
              <div style={{ fontSize: '0.9rem', color: '#8b949e' }}>
                {alert.message}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  )
}

export default Alerts
