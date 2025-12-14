import os
import requests
from app.core.config import decrypt_config_value, load_config_with_decryption

def send_telegram_message(message: str, password: str):
    """
    Send a Telegram message using credentials from application.yml
    Requires Jasypt password to decrypt bot-token and chat-id
    Respects telegram.enabled flag and CI environment variable
    """
    try:
        # Check if running in CI environment - skip Telegram in CI
        if os.environ.get('CI') == 'true':
            print(f"[Telegram disabled in CI] {message[:50]}...")
            return False
        
        config = load_config_with_decryption(password)
        
        if 'telegram' not in config:
            print("⚠️ Telegram config not found")
            return False
        
        # Check if Telegram is enabled in config
        telegram_config = config['telegram']
        enabled = telegram_config.get('enabled', True)
        if not enabled:
            print(f"[Telegram disabled] {message[:50]}...")
            return False
        
        bot_token = decrypt_config_value(telegram_config.get('bot-token'), password)
        chat_id = decrypt_config_value(telegram_config.get('chat-id'), password)
        
        if not bot_token or not chat_id:
            print("⚠️ Telegram credentials missing")
            return False
        
        url = f"https://api.telegram.org/bot{bot_token}/sendMessage"
        payload = {
            "chat_id": chat_id,
            "text": message,
            "parse_mode": "HTML"
        }
        
        response = requests.post(url, json=payload, timeout=10)
        if response.status_code == 200:
            print(f"📱 Telegram: {message[:50]}...")
            return True
        else:
            print(f"⚠️ Telegram failed: {response.status_code}")
            return False
    except Exception as e:
        print(f"⚠️ Telegram error: {e}")
        return False
