import requests
import json
import re

# Centralized paranoid risk manager system prompt for trade veto decisions
TRADE_VETO_SYSTEM_PROMPT = """You are an extremely paranoid, professional risk manager for a fully automated Taiwan stock trading system. Your ONLY goal is capital preservation. Profitability is irrelevant — avoiding losses and drawdowns is everything.

You will receive a full trade proposal containing:
- Proposed trade details (symbol, direction long/short, shares, entry logic, strategy name)
- Current system state (daily P&L, weekly P&L, current drawdown %, trades today, recent win streak/loss streak)
- Market context (volatility level, time of day, session phase)
- Recent news headlines/summaries (in Chinese or English)
- Strategy performance context (recent backtest stats, how long this strategy has been active)

Your job: Decide APPROVE or VETO.

Default to VETO. Only APPROVE if ALL of the following are unambiguously true:

1. News sentiment is clearly neutral or mildly positive. Any negative keyword (e.g., 下跌, 利空, 衰退, 地緣, 貿易戰, 地震, 颱風, 調查, 違規, 警告, 降評, US-China tension, Fed hawkish, earnings miss, etc.) → VETO.
2. No conflicting or high-volume news.
3. System is not in drawdown (>3% daily or >8% weekly) → VETO.
4. Fewer than 3 trades executed today → if more, VETO.
5. No recent losing streak (2+ consecutive losses) → VETO.
6. Trade size is 100 shares or fewer → if more, VETO.
7. Market volatility is normal (not labeled high/choppy) → if high, VETO.
8. Not within first 30 minutes or last 30 minutes of the trading session (09:00–13:30).
9. Strategy has been active for at least 3 days (no brand-new switches) → if new, VETO.
10. If any doubt, uncertainty, missing info, or edge case → VETO.

Respond ONLY with one of these exact formats. No explanation, no extra text:

APPROVE
VETO: <one very short reason phrase>

Examples:
APPROVE
VETO: daily drawdown exceeded
VETO: too many trades today
VETO: negative news keyword
VETO: strategy too new
VETO: high volatility
VETO: size >100 shares
VETO: recent losses
VETO: near session open/close"""


class OllamaService:
    def __init__(self, url: str, model: str):
        self.url = url
        self.model = model

    def generate(self, prompt: str, options: dict = None, system: str = None) -> dict:
        """Generic generation method with optional system prompt"""
        if not self.url or not self.model:
            return {"error": "Ollama not configured"}
            
        try:
            payload = {
                "model": self.model,
                "prompt": prompt,
                "stream": False,
            }
            if system:
                payload["system"] = system
            if options:
                payload["options"] = options

            response = requests.post(
                f"{self.url}/api/generate",
                json=payload,
                timeout=120
            )
            result = response.json().get('response', '')
            return {"response": result}
        except Exception as e:
            return {"error": str(e)}

    def _parse_veto_response(self, response_text: str) -> dict:
        """Parse APPROVE/VETO response strictly"""
        text = response_text.strip()
        
        if text == "APPROVE" or text.startswith("APPROVE"):
            return {"veto": False, "score": 1.0, "reason": "APPROVED"}
        
        veto_match = re.match(r'^VETO:\s*(.+)$', text, re.IGNORECASE)
        if veto_match:
            reason = veto_match.group(1).strip()
            return {"veto": True, "score": 0.0, "reason": reason}
        
        return {"veto": True, "score": 0.0, "reason": "unexpected response format - defaulting to VETO"}

    def call_trade_veto(self, trade_proposal: dict) -> dict:
        """
        Call Ollama with full trade proposal for APPROVE/VETO decision.
        Uses the centralized paranoid risk manager system prompt.
        
        trade_proposal should contain:
        - symbol, direction, shares, entry_logic, strategy_name
        - daily_pnl, weekly_pnl, drawdown_percent, trades_today, win_streak, loss_streak
        - volatility_level, time_of_day, session_phase
        - news_headlines (list)
        - strategy_days_active, recent_backtest_stats
        """
        user_prompt = f"""Trade Proposal:
- Symbol: {trade_proposal.get('symbol', 'N/A')}
- Direction: {trade_proposal.get('direction', 'N/A')}
- Shares: {trade_proposal.get('shares', 'N/A')}
- Entry Logic: {trade_proposal.get('entry_logic', 'N/A')}
- Strategy: {trade_proposal.get('strategy_name', 'N/A')}

System State:
- Daily P&L: {trade_proposal.get('daily_pnl', 'N/A')} TWD
- Weekly P&L: {trade_proposal.get('weekly_pnl', 'N/A')} TWD
- Current Drawdown: {trade_proposal.get('drawdown_percent', 'N/A')}%
- Trades Today: {trade_proposal.get('trades_today', 'N/A')}
- Win Streak: {trade_proposal.get('win_streak', 'N/A')}
- Loss Streak: {trade_proposal.get('loss_streak', 'N/A')}

Market Context:
- Volatility: {trade_proposal.get('volatility_level', 'N/A')}
- Time: {trade_proposal.get('time_of_day', 'N/A')}
- Session Phase: {trade_proposal.get('session_phase', 'N/A')}

News Headlines:
{chr(10).join(f"- {h}" for h in trade_proposal.get('news_headlines', []))}

Strategy Context:
- Days Active: {trade_proposal.get('strategy_days_active', 'N/A')}
- Recent Backtest Stats: {trade_proposal.get('recent_backtest_stats', 'N/A')}"""

        try:
            result = self.generate(
                prompt=user_prompt,
                system=TRADE_VETO_SYSTEM_PROMPT,
                options={"temperature": 0.1}
            )
            if "error" in result:
                return {"veto": True, "score": 0.0, "reason": f"Analysis failed: {result['error']}"}
            
            return self._parse_veto_response(result.get('response', ''))
        except Exception as e:
            return {"veto": True, "score": 0.0, "reason": f"Analysis failed: {str(e)}"}

    def call_llama_news_veto(self, headlines: list) -> dict:
        """
        Call Ollama for news-based veto decision.
        Uses a simplified prompt for faster response.
        """
        # Simple, direct prompt without the massive system prompt
        full_prompt = f"""Analyze these Taiwan stock market news headlines. Respond ONLY with "APPROVE" or "VETO: reason".

Headlines:
{chr(10).join(f"- {h}" for h in headlines)}

Rules:
- If ANY headline mentions: 下跌, 利空, 衰退, 地緣, 貿易戰, crash, decline, warning, negative → VETO
- If neutral or mildly positive → APPROVE
- If uncertain → VETO

Response:"""

        try:
            result = self.generate(
                prompt=full_prompt,
                system=None,  # No system prompt for speed
                options={"temperature": 0.1}
            )
            if "error" in result:
                return {"veto": True, "score": 0.0, "reason": f"Analysis failed: {result['error']}"}
            
            return self._parse_veto_response(result.get('response', ''))
        except Exception as e:
            return {"veto": True, "score": 0.0, "reason": f"Analysis failed: {str(e)}"}

    def call_llama_error_explanation(self, error_type: str, error_message: str, context: str = "") -> dict:
        """Call Ollama to generate human-readable error explanation"""
        if not self.url or not self.model:
            return {
                "explanation": error_message,
                "suggestion": "System is initializing. Please try again in a moment.",
                "severity": "medium"
            }
        
        prompt = f"""You are an expert trading system support agent. Explain this error in simple, actionable terms for a trader.

Error Type: {error_type}
Error Message: {error_message}
Context: {context}

Respond ONLY with valid JSON:
{{"explanation": "brief user-friendly explanation", "suggestion": "what the user should do", "severity": "low/medium/high"}}

Be concise, friendly, and actionable. Focus on what the trader can do to resolve the issue."""

        try:
            result = self.generate(prompt, options={"temperature": 0.3})
            if "error" in result:
                return {
                    "explanation": error_message,
                    "suggestion": "Please check the logs or contact support",
                    "severity": "medium"
                }
            return json.loads(result['response'])
        except:
            return {
                "explanation": error_message,
                "suggestion": "Please check the logs or contact support",
                "severity": "medium"
            }
