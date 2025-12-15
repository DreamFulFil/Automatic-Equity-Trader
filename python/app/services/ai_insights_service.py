"""
AI Insights Service - Enhanced Ollama Integration

Provides intelligent analysis and recommendations for trading decisions.
Converts complex numerical data into actionable insights for beginners.
"""

from typing import Dict, List, Optional
import json
from .ollama_service import OllamaService


class AIInsightsService:
    """
    AI-powered analysis service that helps users understand:
    - Which strategies are performing best
    - Which stocks to trade
    - Risk warnings and position sizing
    - Market conditions and recommendations
    """
    
    def __init__(self, ollama_service: OllamaService):
        self.ollama = ollama_service
    
    def analyze_strategy_performance(self, performances: List[Dict]) -> Dict:
        """
        Analyze strategy performance data and generate beginner-friendly insights
        
        Args:
            performances: List of strategy performance metrics
            
        Returns:
            Dict with insights, recommendations, and warnings
        """
        if not performances:
            return {
                "summary": "No performance data available yet",
                "recommendation": "Continue running shadow mode to gather data",
                "risk_level": "UNKNOWN"
            }
        
        # Sort by Sharpe ratio (risk-adjusted returns)
        sorted_perfs = sorted(performances, key=lambda x: x.get('sharpe_ratio', 0), reverse=True)
        best = sorted_perfs[0] if sorted_perfs else None
        
        prompt = f"""You are a trading mentor helping a beginner understand their strategy performance.

Performance Data:
{json.dumps(sorted_perfs[:5], indent=2)}

Provide a brief analysis (3-4 sentences) covering:
1. Which strategy is performing best and why
2. Risk level assessment (Low/Medium/High)
3. One actionable recommendation

Keep it simple and actionable. No jargon."""

        result = self.ollama.generate(prompt)
        
        return {
            "best_strategy": best.get('strategy_name') if best else None,
            "analysis": result.get('response', 'Analysis unavailable'),
            "top_3_strategies": [p['strategy_name'] for p in sorted_perfs[:3]],
            "risk_level": self._assess_risk_level(best) if best else "UNKNOWN"
        }
    
    def analyze_stock_performance(self, stock_data: List[Dict]) -> Dict:
        """
        Analyze which stocks are performing best with which strategies
        
        Args:
            stock_data: List of stock-strategy performance combinations
            
        Returns:
            Actionable insights about stock selection
        """
        if not stock_data:
            return {
                "summary": "No stock performance data available",
                "recommendation": "Run backtests to gather data"
            }
        
        # Group by stock and find best strategy for each
        stock_best = {}
        for data in stock_data:
            symbol = data.get('symbol')
            strategy = data.get('strategy_name')
            sharpe = data.get('sharpe_ratio', 0)
            
            if symbol not in stock_best or sharpe > stock_best[symbol]['sharpe']:
                stock_best[symbol] = {
                    'strategy': strategy,
                    'sharpe': sharpe,
                    'return_pct': data.get('total_return_pct', 0)
                }
        
        # Prepare summary
        top_stocks = sorted(stock_best.items(), key=lambda x: x[1]['sharpe'], reverse=True)[:5]
        
        prompt = f"""You are helping a risk-averse beginner choose stocks to trade.

Top 5 Stocks Analysis:
{json.dumps(dict(top_stocks), indent=2)}

Provide:
1. Which stock looks safest (lowest risk, steady returns)
2. Suggested position size (small/medium for 80k TWD capital)
3. One warning about diversification

Be encouraging but cautious. 3-4 sentences max."""

        result = self.ollama.generate(prompt)
        
        return {
            "top_stocks": [stock for stock, _ in top_stocks],
            "best_matches": stock_best,
            "analysis": result.get('response', 'Analysis unavailable'),
            "diversification_score": len(stock_best)
        }
    
    def analyze_daily_report(self, report_data: Dict) -> Dict:
        """
        Analyze daily performance report and generate insights
        
        Args:
            report_data: Daily performance metrics
            
        Returns:
            Easy-to-understand daily summary
        """
        prompt = f"""You are a trading mentor reviewing today's performance with a beginner.

Today's Performance:
- Main Strategy: {report_data.get('main_strategy', 'N/A')}
- Daily Return: {report_data.get('daily_return_pct', 0):.2f}%
- Weekly Return: {report_data.get('weekly_return_pct', 0):.2f}%
- Total Trades: {report_data.get('total_trades', 0)}
- Win Rate: {report_data.get('win_rate_pct', 0):.1f}%
- Max Drawdown: {report_data.get('max_drawdown_pct', 0):.2f}%

Provide a brief daily summary:
1. How did we do today? (Good/Okay/Concerning)
2. One thing to be happy about
3. One thing to watch for tomorrow

Keep it encouraging and simple. 3-4 sentences."""

        result = self.ollama.generate(prompt)
        
        return {
            "summary": result.get('response', 'Daily analysis unavailable'),
            "sentiment": self._determine_sentiment(report_data),
            "action_needed": self._needs_attention(report_data)
        }
    
    def generate_position_sizing_advice(self, 
                                       capital: float,
                                       stock_price: float,
                                       risk_level: str,
                                       equity: float) -> Dict:
        """
        Generate beginner-friendly position sizing advice
        
        Args:
            capital: Available capital
            stock_price: Current stock price
            risk_level: LOW/MEDIUM/HIGH
            equity: Current account equity
            
        Returns:
            Position sizing recommendation
        """
        base_capital = 80000
        increment_capital = 20000
        
        # Calculate how many increments above base
        increments = max(0, int((equity - base_capital) / increment_capital))
        
        prompt = f"""You are helping a risk-averse beginner decide position size.

Situation:
- Current Equity: {equity:,.0f} TWD
- Stock Price: {stock_price:,.0f} TWD
- Risk Level: {risk_level}
- Additional Capital Earned: {increments * increment_capital:,.0f} TWD

For a risk-averse person wanting steady 5% monthly returns:
1. Suggest shares to buy (use odd lots, max ~10% of equity per position)
2. Explain why this size is safe
3. Mention when to scale up more

2-3 sentences, be specific with numbers."""

        result = self.ollama.generate(prompt)
        
        # Calculate safe position size (max 10% of equity for risk-averse)
        max_position_value = equity * 0.10
        suggested_shares = int(max_position_value / stock_price)
        
        return {
            "suggested_shares": suggested_shares,
            "position_value": suggested_shares * stock_price,
            "explanation": result.get('response', 'Position sizing advice unavailable'),
            "risk_percentage": (suggested_shares * stock_price / equity) * 100
        }
    
    def analyze_risk_metrics(self, metrics: Dict) -> Dict:
        """
        Analyze risk metrics and warn user if needed
        
        Args:
            metrics: Risk metrics (drawdown, volatility, etc.)
            
        Returns:
            Risk assessment and warnings
        """
        max_dd = abs(metrics.get('max_drawdown_pct', 0))
        volatility = metrics.get('volatility', 0)
        sharpe = metrics.get('sharpe_ratio', 0)
        
        # Risk thresholds for risk-averse user
        HIGH_RISK_DD = 15  # 15% drawdown is concerning
        HIGH_RISK_VOL = 3  # High volatility
        LOW_SHARPE = 0.5   # Poor risk-adjusted returns
        
        warnings = []
        if max_dd > HIGH_RISK_DD:
            warnings.append(f"High drawdown of {max_dd:.1f}% - strategy might be too risky")
        if volatility > HIGH_RISK_VOL:
            warnings.append(f"High volatility detected - expect price swings")
        if sharpe < LOW_SHARPE:
            warnings.append(f"Low Sharpe ratio ({sharpe:.2f}) - returns may not justify risk")
        
        risk_level = "HIGH" if len(warnings) >= 2 else "MEDIUM" if warnings else "LOW"
        
        prompt = f"""You are warning a risk-averse beginner about strategy risks.

Risk Metrics:
- Max Drawdown: {max_dd:.1f}%
- Sharpe Ratio: {sharpe:.2f}
- Volatility: {volatility:.2f}

Risk Level: {risk_level}

If risk is MEDIUM or HIGH, suggest:
1. Should they reduce position size?
2. Should they switch strategies?

Be protective and cautious. 2-3 sentences."""

        result = self.ollama.generate(prompt) if risk_level != "LOW" else {"response": "Risk levels look acceptable. Continue monitoring."}
        
        return {
            "risk_level": risk_level,
            "warnings": warnings,
            "advice": result.get('response', 'Risk analysis unavailable'),
            "action_required": len(warnings) >= 2
        }
    
    def explain_strategy_switch(self, 
                               current_strategy: str,
                               recommended_strategy: str,
                               reason: str) -> str:
        """
        Explain why system wants to switch strategies
        
        Args:
            current_strategy: Current main strategy
            recommended_strategy: Proposed new strategy
            reason: Technical reason for switch
            
        Returns:
            Beginner-friendly explanation
        """
        prompt = f"""You are explaining a strategy change to a beginner trader.

Current Strategy: {current_strategy}
Proposed Strategy: {recommended_strategy}
Technical Reason: {reason}

Explain in simple terms:
1. Why the change is suggested
2. What to expect (better/safer results?)
3. Should they approve it?

Be honest if the change seems risky. 3-4 sentences."""

        result = self.ollama.generate(prompt)
        return result.get('response', 'Strategy change explanation unavailable')
    
    def _assess_risk_level(self, performance: Dict) -> str:
        """Assess risk level from performance metrics"""
        max_dd = abs(performance.get('max_drawdown_pct', 0))
        sharpe = performance.get('sharpe_ratio', 0)
        
        if max_dd > 15 or sharpe < 0.5:
            return "HIGH"
        elif max_dd > 10 or sharpe < 1.0:
            return "MEDIUM"
        return "LOW"
    
    def _determine_sentiment(self, report_data: Dict) -> str:
        """Determine sentiment from report data"""
        daily_return = report_data.get('daily_return_pct', 0)
        win_rate = report_data.get('win_rate_pct', 0)
        
        if daily_return > 1 and win_rate > 60:
            return "POSITIVE"
        elif daily_return < -2 or win_rate < 40:
            return "NEGATIVE"
        return "NEUTRAL"
    
    def _needs_attention(self, report_data: Dict) -> bool:
        """Check if user attention is needed"""
        daily_return = report_data.get('daily_return_pct', 0)
        max_dd = abs(report_data.get('max_drawdown_pct', 0))
        
        return daily_return < -3 or max_dd > 15
