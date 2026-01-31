# Automatic Equity Trader Dashboard

Real-time React dashboard for monitoring the Automatic Equity Trader system.

## Features

- **Portfolio Overview**: Real-time P&L metrics (daily, weekly, monthly, total), active positions, and risk metrics
- **Equity Curve**: 30-day cumulative P&L visualization with interactive charts
- **Active Positions**: Live position tracking with unrealized P&L
- **Strategy Performance**: Top-performing strategies ranked by 30-day performance
- **Recent Trades**: Activity log of recent buy/sell executions
- **Alert Center**: Real-time alerts for risk events, trades, system status, and performance milestones

## Prerequisites

- Node.js 18+ and npm
- Backend API running on `http://localhost:8080`

## Installation

```bash
cd dashboard
npm install
```

## Development

Start the development server with hot reload:

```bash
npm run dev
```

The dashboard will be available at `http://localhost:3000`.

## Production Build

Build for production:

```bash
npm run build
```

Serve the production build:

```bash
npm run preview
```

## API Integration

The dashboard connects to the following REST endpoints:

- `GET /api/dashboard/overview` - Portfolio overview metrics
- `GET /api/dashboard/equity-curve?days=30` - Equity curve data
- `GET /api/dashboard/positions` - Current positions
- `GET /api/dashboard/strategies?limit=10` - Top strategies
- `GET /api/dashboard/trades?limit=20` - Recent trades
- `GET /api/dashboard/alerts?limit=20` - Recent alerts
- `POST /api/dashboard/alerts/{id}/read` - Mark alert as read
- `POST /api/dashboard/alerts/read-all` - Mark all alerts as read

## Auto-Refresh

Data is automatically refreshed every 30 seconds to ensure real-time monitoring.

## Dark Theme

The dashboard uses a dark theme optimized for trading environments, reducing eye strain during extended monitoring sessions.

## Architecture

- **React 18**: Component-based UI
- **Vite**: Fast development server and build tool
- **Recharts**: Interactive charting library
- **Axios**: HTTP client for API calls
- **CSS**: Custom styling with dark theme

## Directory Structure

```
dashboard/
├── src/
│   ├── components/       # React components
│   │   ├── Overview.jsx
│   │   ├── EquityCurve.jsx
│   │   ├── Positions.jsx
│   │   ├── Strategies.jsx
│   │   ├── RecentTrades.jsx
│   │   └── Alerts.jsx
│   ├── App.jsx           # Main application component
│   ├── App.css           # Application styles
│   ├── main.jsx          # Application entry point
│   └── index.css         # Global styles
├── index.html            # HTML template
├── vite.config.js        # Vite configuration
└── package.json          # Dependencies and scripts
```
