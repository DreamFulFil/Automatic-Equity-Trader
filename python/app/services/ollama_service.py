import requests
import json

class OllamaService:
    def __init__(self, url: str, model: str):
        self.url = url
        self.model = model

    def generate(self, prompt: str, options: dict = None) -> dict:
        """Generic generation method"""
        if not self.url or not self.model:
            return {"error": "Ollama not configured"}
            
        try:
            payload = {
                "model": self.model,
                "prompt": prompt,
                "stream": False,
            }
            if options:
                payload["options"] = options

            response = requests.post(
                f"{self.url}/api/generate",
                json=payload,
                timeout=30 # Increased timeout for longer generations
            )
            result = response.json().get('response', '')
            return {"response": result}
        except Exception as e:
            return {"error": str(e)}

    def call_llama_news_veto(self, headlines: list) -> dict:
        """Call Ollama Llama 3.1 8B for news sentiment veto"""
        prompt = f"""You are a Taiwan stock market news analyst. Analyze these headlines and decide if trading should be VETOED due to major negative news.

Headlines:
{chr(10).join(f"- {h}" for h in headlines)}

Respond ONLY with valid JSON:
{{"veto": true/false, "score": 0.0-1.0, "reason": "brief explanation"}}

Veto if: geopolitical crisis, major crash, regulatory halt, war.
Score: 0.0=very bearish, 0.5=neutral, 1.0=very bullish"""

        try:
            result = self.generate(prompt, options={"temperature": 0.3})
            if "error" in result:
                return {"veto": False, "score": 0.5, "reason": f"Analysis failed: {result['error']}"}
            
            return json.loads(result['response'])
        except:
            return {"veto": False, "score": 0.5, "reason": "Analysis failed (parsing)"}

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
