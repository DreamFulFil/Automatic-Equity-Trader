import requests
import json
import re
from typing import Dict, List, Optional

# Weight constants for risk scoring
DRAWDOWN_WEIGHT = 0.30
NEWS_WEIGHT = 0.20
VOLATILITY_WEIGHT = 0.20
STREAK_WEIGHT = 0.15
SIZE_WEIGHT = 0.15

# Thresholds
DEFAULT_VETO_THRESHOLD = 70.0
HIGH_CONVICTION_THRESHOLD = 80.0  # For signals with >0.9 confidence
LOW_CONVICTION_THRESHOLD = 60.0   # For signals with <0.5 confidence

# Risk limits for scoring
DAILY_DRAWDOWN_DANGER = 3.0   # > 3% daily drawdown = max risk
WEEKLY_DRAWDOWN_DANGER = 8.0  # > 8% weekly drawdown = max risk
MAX_TRADES_PER_DAY = 5
MAX_SHARES = 200  # Relaxed from 100 shares
LOSS_STREAK_DANGER = 3

# Negative news keywords (Chinese and English)
NEGATIVE_NEWS_KEYWORDS = [
    '下跌', '利空', '衰退', '地緣', '貿易戰', '地震', '颱風', '調查', '違規', '警告', '降評',
    'crash', 'decline', 'warning', 'negative', 'sell-off', 'plunge', 'recession',
    'tension', 'hawkish', 'miss', 'investigation', 'fraud'
]

# Centralized risk-aware system prompt for trade veto decisions
# Changed from binary APPROVE/VETO to calibrated risk scoring
TRADE_VETO_SYSTEM_PROMPT = """You are a professional risk manager for a Taiwan stock trading system. 
Your role is to provide a calibrated risk assessment, not a binary decision.

You will receive a trade proposal with:
- Trade details (symbol, direction, shares, entry logic, strategy name)
- System state (daily P&L, weekly P&L, drawdown %, trades today, win/loss streak)
- Market context (volatility level, time of day, session phase)
- News headlines (Chinese or English)
- Strategy context (days active, backtest stats)
- Signal confidence (0.0-1.0 if available)

IMPORTANT: High-confidence signals (>0.9) can tolerate more risk.
Low-confidence signals (<0.5) require stricter standards.

Assess each risk factor on 0-100 scale:
1. DRAWDOWN RISK: 0=no drawdown, 100=severe drawdown (>3% daily or >8% weekly)
2. NEWS RISK: 0=positive/neutral news, 100=multiple negative headlines
3. VOLATILITY RISK: 0=low volatility, 100=extreme volatility
4. STREAK RISK: 0=winning streak, 100=3+ consecutive losses
5. SIZE RISK: 0=small position, 100=excessive size/frequency

Then provide overall recommendation:
- APPROVE if weighted risk score < threshold (default 70, adjusted by confidence)
- VETO if weighted risk score >= threshold

Respond ONLY in this JSON format:
{"drawdown_risk": <0-100>, "news_risk": <0-100>, "volatility_risk": <0-100>, "streak_risk": <0-100>, "size_risk": <0-100>, "total_score": <0-100>, "recommendation": "APPROVE" or "VETO", "reason": "<brief reason>"}

Examples:
{"drawdown_risk": 20, "news_risk": 0, "volatility_risk": 30, "streak_risk": 10, "size_risk": 15, "total_score": 18.5, "recommendation": "APPROVE", "reason": "Low risk across all factors"}
{"drawdown_risk": 80, "news_risk": 75, "volatility_risk": 60, "streak_risk": 70, "size_risk": 40, "total_score": 68.5, "recommendation": "VETO", "reason": "High drawdown and negative news"}"""

# Legacy system prompt for backward compatibility
LEGACY_VETO_SYSTEM_PROMPT = """You are an extremely paranoid, professional risk manager for a fully automated Taiwan stock trading system. Your ONLY goal is capital preservation.

Default to VETO. Only APPROVE if ALL conditions are clearly safe:
1. News sentiment is neutral or positive (no negative keywords)
2. System is not in drawdown (>3% daily or >8% weekly)
3. Fewer than 5 trades today
4. No recent losing streak (3+ consecutive losses)
5. Trade size is 200 shares or fewer
6. Market volatility is normal
7. Not within first/last 30 minutes of session (09:00-13:30)
8. Strategy has been active for at least 3 days

Respond ONLY with: APPROVE or VETO: <short reason>"""


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
                timeout=15  # Reduced from 120s to 15s for production performance
            )
            result = response.json().get('response', '')
            return {"response": result}
        except Exception as e:
            return {"error": str(e)}

    def _parse_veto_response(self, response_text: str) -> dict:
        """Parse APPROVE/VETO response strictly (legacy binary format)"""
        text = response_text.strip()
        
        if text == "APPROVE" or text.startswith("APPROVE"):
            return {"veto": False, "score": 1.0, "reason": "APPROVED"}
        
        veto_match = re.match(r'^VETO:\s*(.+)$', text, re.IGNORECASE)
        if veto_match:
            reason = veto_match.group(1).strip()
            return {"veto": True, "score": 0.0, "reason": reason}
        
        return {"veto": True, "score": 0.0, "reason": "unexpected response format - defaulting to VETO"}

    def _parse_risk_score_response(self, response_text: str, signal_confidence: Optional[float] = None) -> dict:
        """Parse calibrated risk score JSON response"""
        text = response_text.strip()
        
        try:
            # Try to parse as JSON
            data = json.loads(text)
            
            # Extract individual risk scores
            drawdown_risk = float(data.get('drawdown_risk', 50))
            news_risk = float(data.get('news_risk', 50))
            volatility_risk = float(data.get('volatility_risk', 50))
            streak_risk = float(data.get('streak_risk', 50))
            size_risk = float(data.get('size_risk', 50))
            
            # Calculate weighted score if not provided
            total_score = data.get('total_score')
            if total_score is None:
                total_score = (
                    drawdown_risk * DRAWDOWN_WEIGHT +
                    news_risk * NEWS_WEIGHT +
                    volatility_risk * VOLATILITY_WEIGHT +
                    streak_risk * STREAK_WEIGHT +
                    size_risk * SIZE_WEIGHT
                )
            
            # Determine veto threshold based on signal confidence
            threshold = DEFAULT_VETO_THRESHOLD
            confidence_adjustment = 1.0
            
            if signal_confidence is not None:
                if signal_confidence >= 0.9:
                    threshold = HIGH_CONVICTION_THRESHOLD
                    confidence_adjustment = 0.8  # Reduce effective score by 20%
                elif signal_confidence < 0.5:
                    threshold = LOW_CONVICTION_THRESHOLD
                    confidence_adjustment = 1.2  # Increase effective score by 20%
            
            adjusted_score = total_score * confidence_adjustment
            
            recommendation = data.get('recommendation', 'VETO')
            # Override recommendation based on adjusted score and threshold
            should_veto = adjusted_score >= threshold
            
            return {
                "veto": should_veto,
                "risk_score": adjusted_score,
                "raw_score": total_score,
                "threshold": threshold,
                "confidence_adjustment": confidence_adjustment,
                "reason": data.get('reason', 'Risk score threshold exceeded' if should_veto else 'APPROVED'),
                "breakdown": {
                    "drawdown": drawdown_risk,
                    "news": news_risk,
                    "volatility": volatility_risk,
                    "streak": streak_risk,
                    "size": size_risk
                }
            }
        except json.JSONDecodeError:
            # Fall back to legacy parsing if JSON fails
            legacy_result = self._parse_veto_response(text)
            legacy_result["risk_score"] = 100.0 if legacy_result["veto"] else 0.0
            return legacy_result

    def _calculate_local_risk_score(self, trade_proposal: dict) -> dict:
        """
        Calculate risk score locally without LLM call.
        This is a fast fallback when LLM is unavailable or for quick checks.
        """
        # Calculate individual risk components
        drawdown_risk = self._calculate_drawdown_risk(trade_proposal)
        news_risk = self._calculate_news_risk(trade_proposal.get('news_headlines', []))
        volatility_risk = self._calculate_volatility_risk(trade_proposal.get('volatility_level', 'normal'))
        streak_risk = self._calculate_streak_risk(
            trade_proposal.get('win_streak', 0),
            trade_proposal.get('loss_streak', 0)
        )
        size_risk = self._calculate_size_risk(
            trade_proposal.get('shares', 0),
            trade_proposal.get('trades_today', 0)
        )
        
        # Weighted total
        total_score = (
            drawdown_risk * DRAWDOWN_WEIGHT +
            news_risk * NEWS_WEIGHT +
            volatility_risk * VOLATILITY_WEIGHT +
            streak_risk * STREAK_WEIGHT +
            size_risk * SIZE_WEIGHT
        )
        
        # Apply confidence adjustment
        signal_confidence = trade_proposal.get('signal_confidence')
        threshold = DEFAULT_VETO_THRESHOLD
        confidence_adjustment = 1.0
        
        if signal_confidence is not None:
            if signal_confidence >= 0.9:
                threshold = HIGH_CONVICTION_THRESHOLD
                confidence_adjustment = 0.8
            elif signal_confidence < 0.5:
                threshold = LOW_CONVICTION_THRESHOLD
                confidence_adjustment = 1.2
        
        adjusted_score = total_score * confidence_adjustment
        should_veto = adjusted_score >= threshold
        
        # Determine reason
        risk_factors = [
            (drawdown_risk, "drawdown"),
            (news_risk, "negative news"),
            (volatility_risk, "volatility"),
            (streak_risk, "losing streak"),
            (size_risk, "position size/frequency")
        ]
        max_risk = max(risk_factors, key=lambda x: x[0])
        reason = f"High {max_risk[1]} risk" if should_veto else "APPROVED"
        
        return {
            "veto": should_veto,
            "risk_score": adjusted_score,
            "raw_score": total_score,
            "threshold": threshold,
            "confidence_adjustment": confidence_adjustment,
            "reason": reason,
            "breakdown": {
                "drawdown": drawdown_risk,
                "news": news_risk,
                "volatility": volatility_risk,
                "streak": streak_risk,
                "size": size_risk
            }
        }
    
    def _calculate_drawdown_risk(self, trade_proposal: dict) -> float:
        """Calculate drawdown risk (0-100)"""
        drawdown = trade_proposal.get('drawdown_percent', 0)
        try:
            drawdown = float(str(drawdown).replace('%', ''))
        except (ValueError, TypeError):
            drawdown = 0
        
        if drawdown <= 0:
            return 0
        return min(100, (drawdown / DAILY_DRAWDOWN_DANGER) * 100)
    
    def _calculate_news_risk(self, headlines: list) -> float:
        """Calculate news sentiment risk (0-100)"""
        if not headlines:
            return 0
        
        negative_count = 0
        for headline in headlines:
            if headline:
                headline_lower = headline.lower()
                for keyword in NEGATIVE_NEWS_KEYWORDS:
                    if keyword.lower() in headline_lower:
                        negative_count += 1
                        break
        
        return min(100, negative_count * 25.0)
    
    def _calculate_volatility_risk(self, volatility_level: str) -> float:
        """Calculate volatility risk (0-100)"""
        if not volatility_level:
            return 30  # Unknown = moderate risk
        
        level = volatility_level.lower()
        if level == 'low':
            return 0
        elif level == 'normal':
            return 20
        elif level == 'high':
            return 60
        elif level in ('extreme', 'choppy'):
            return 100
        return 30
    
    def _calculate_streak_risk(self, win_streak: int, loss_streak: int) -> float:
        """Calculate streak risk (0-100)"""
        try:
            loss_streak = int(loss_streak)
            win_streak = int(win_streak)
        except (ValueError, TypeError):
            return 25
        
        if loss_streak >= LOSS_STREAK_DANGER:
            return 100
        if loss_streak >= 2:
            return 70
        if loss_streak == 1:
            return 40
        if win_streak >= 3:
            return 10
        return 25
    
    def _calculate_size_risk(self, shares: int, trades_today: int) -> float:
        """Calculate size and frequency risk (0-100)"""
        try:
            shares = int(shares)
            trades_today = int(trades_today)
        except (ValueError, TypeError):
            return 25
        
        # Size risk
        if shares > MAX_SHARES:
            size_score = 100
        elif shares > 100:
            size_score = ((shares - 100) / (MAX_SHARES - 100)) * 50 + 25
        else:
            size_score = (shares / 100) * 25
        
        # Frequency risk
        if trades_today >= MAX_TRADES_PER_DAY:
            freq_score = 100
        elif trades_today >= 3:
            freq_score = 60
        else:
            freq_score = trades_today * 15
        
        return (size_score + freq_score) / 2

    def call_trade_veto(self, trade_proposal: dict) -> dict:
        """
        Call Ollama with full trade proposal for APPROVE/VETO decision.
        Uses the centralized risk manager system prompt.
        
        trade_proposal should contain:
        - symbol, direction, shares, entry_logic, strategy_name
        - daily_pnl, weekly_pnl, drawdown_percent, trades_today, win_streak, loss_streak
        - volatility_level, time_of_day, session_phase
        - news_headlines (list)
        - strategy_days_active, recent_backtest_stats
        - signal_confidence (optional, 0.0-1.0)
        """
        user_prompt = f"""Trade Proposal:
- Symbol: {trade_proposal.get('symbol', 'N/A')}
- Direction: {trade_proposal.get('direction', 'N/A')}
- Shares: {trade_proposal.get('shares', 'N/A')}
- Entry Logic: {trade_proposal.get('entry_logic', 'N/A')}
- Strategy: {trade_proposal.get('strategy_name', 'N/A')}
- Signal Confidence: {trade_proposal.get('signal_confidence', 'N/A')}

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
                system=LEGACY_VETO_SYSTEM_PROMPT,  # Use legacy for backward compatibility
                options={"temperature": 0.1}
            )
            if "error" in result:
                return {"veto": True, "score": 0.0, "reason": f"Analysis failed: {result['error']}"}
            
            return self._parse_veto_response(result.get('response', ''))
        except Exception as e:
            return {"veto": True, "score": 0.0, "reason": f"Analysis failed: {str(e)}"}

    def call_trade_risk_score(self, trade_proposal: dict, use_llm: bool = True) -> dict:
        """
        Calculate calibrated risk score for a trade proposal.
        
        This is the recommended method for Phase 5 - it provides a weighted risk score
        instead of a binary APPROVE/VETO decision, allowing high-confidence signals
        to tolerate more individual risk factors.
        
        Args:
            trade_proposal: Dict with trade details, system state, market context, etc.
            use_llm: If True, use LLM for risk assessment. If False, use local calculation only.
        
        Returns:
            Dict with:
            - veto: bool - whether to veto the trade
            - risk_score: float - adjusted risk score (0-100)
            - raw_score: float - unadjusted risk score
            - threshold: float - veto threshold used
            - reason: str - reason for decision
            - breakdown: dict - individual risk component scores
        """
        if not use_llm or not self.url or not self.model:
            # Use local calculation (fast, no LLM call)
            return self._calculate_local_risk_score(trade_proposal)
        
        signal_confidence = trade_proposal.get('signal_confidence')
        
        user_prompt = f"""Trade Proposal:
- Symbol: {trade_proposal.get('symbol', 'N/A')}
- Direction: {trade_proposal.get('direction', 'N/A')}
- Shares: {trade_proposal.get('shares', 'N/A')}
- Entry Logic: {trade_proposal.get('entry_logic', 'N/A')}
- Strategy: {trade_proposal.get('strategy_name', 'N/A')}
- Signal Confidence: {signal_confidence if signal_confidence is not None else 'N/A'}

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
- Recent Backtest Stats: {trade_proposal.get('recent_backtest_stats', 'N/A')}

Provide risk assessment as JSON with scores 0-100 for each factor."""

        try:
            result = self.generate(
                prompt=user_prompt,
                system=TRADE_VETO_SYSTEM_PROMPT,  # Use new risk-scoring prompt
                options={"temperature": 0.1}
            )
            if "error" in result:
                # Fall back to local calculation on LLM error
                return self._calculate_local_risk_score(trade_proposal)
            
            return self._parse_risk_score_response(
                result.get('response', ''),
                signal_confidence
            )
        except Exception as e:
            # Fall back to local calculation on exception
            local_result = self._calculate_local_risk_score(trade_proposal)
            local_result["fallback_reason"] = str(e)
            return local_result

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
