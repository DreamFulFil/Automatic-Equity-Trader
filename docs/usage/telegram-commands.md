# Telegram Bot Commands

## Command Reference

| Command | Parameters | Description | Category |
|---------|------------|-------------|----------|
| `/status` | None | Display current trading status, positions, P&L, and active strategy | Control |
| `/pause` | None | Temporarily halt trading (positions remain open) | Control |
| `/resume` | None | Resume trading after pause | Control |
| `/close` | None | Close all open positions immediately | Control |
| `/shutdown` | None | Gracefully shutdown the trading bot and all services | Control |
| `/help` | None | Display list of available commands and their usage | Information |
| `/agent` | None | Query the AI agent (Ollama Llama 3.1) about trading decisions or market conditions | Information |
| `/talk` | `[message]` | Send a message to the AI agent for analysis or advice | Information |
| `/insight` | None | Get AI-generated market insights and trading recommendations | Information |
| `/golive` | None | Initiate transition from simulation to live trading (requires confirmation within 10 minutes) | Trading Mode |
| `/confirm_live` | None | Confirm live trading activation (must follow `/golive` within 10-minute window) | Trading Mode |
| `/backtosim` | None | Revert from live trading back to simulation mode | Trading Mode |
| `/changeshare` | `[contracts]` | Change the number of contracts per trade (min: 1, max: margin limited). Example: `/changeshare 2` | Position Sizing |
| `/changeincrement` | `[amount]` | Adjust position sizing increment for scaling strategies (amount in TWD). Example: `/changeincrement 5000` | Position Sizing |
| `/select_strategy` | `[strategy_name]` | Manually select a specific trading strategy. Example: `/select_strategy AntiWhipsaw_v2` | Strategy |
| `/list_strategies` | None | Display all available trading strategies with performance metrics | Strategy |

## Safety Features

- **Go-Live Protection**: Requires two-step confirmation (`/golive` → `/confirm_live`) within 10-minute window
- **Command Validation**: All commands are validated before execution
- **State Management**: Commands respect current trading state (sim vs live)
- **Error Handling**: Clear error messages for invalid commands or states

## Notes

- All commands are case-insensitive
- Commands are logged for audit purposes
- Telegram bot requires valid `TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID` in configuration
- Rate limiting applies to prevent spam (max 10 commands per minute)
