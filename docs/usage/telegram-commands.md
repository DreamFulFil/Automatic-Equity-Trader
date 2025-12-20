# Telegram Bot Commands

## Control Commands

### `/status`
Display current trading status, positions, P&L, and active strategy.

### `/pause`
Temporarily halt trading (positions remain open).

### `/resume`
Resume trading after pause.

### `/close`
Close all open positions immediately.

### `/shutdown`
Gracefully shutdown the trading bot and all services.

## Information Commands

### `/help`
Display list of available commands and their usage.

### `/agent`
Query the AI agent (Ollama Llama 3.1) about trading decisions or market conditions.

### `/talk [message]`
Send a message to the AI agent for analysis or advice.

### `/insight`
Get AI-generated market insights and trading recommendations.

## Trading Mode Commands

### `/golive`
Initiate transition from simulation to live trading (requires confirmation within 10 minutes).

### `/confirm_live`
Confirm live trading activation (must follow `/golive` within 10-minute window).

### `/backtosim`
Revert from live trading back to simulation mode.

## Position Sizing Commands

### `/changeshare [contracts]`
Change the number of contracts per trade.
- Example: `/changeshare 2`
- Minimum: 1 contract
- Maximum: Limited by margin requirements

### `/changeincrement [amount]`
Adjust position sizing increment for scaling strategies.
- Example: `/changeincrement 5000`
- Amount in TWD

## Strategy Selection Commands

### `/select_strategy [strategy_name]`
Manually select a specific trading strategy.
- Example: `/select_strategy AntiWhipsaw_v2`
- Use `/list_strategies` to see available options

### `/list_strategies`
Display all available trading strategies with performance metrics.

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
