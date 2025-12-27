import requests
from app.core.config import decrypt_config_value, load_config_with_decryption

def send_telegram_message(message: str, password: str):
    """
    Send a Telegram message using credentials from application.yml
    Requires Jasypt password to decrypt bot-token and chat-id
    """
    try:
        # We can reuse load_config_with_decryption, but it might be heavy to reload every time.
        # Ideally, we should cache the config or pass it in.
        # For now, to keep it simple and compatible with the existing logic, we'll load it.
        # Optimization: In a real app, config should be a singleton.
        
        config = load_config_with_decryption(password)
        
        if 'telegram' not in config:
            print("⚠️ Telegram config not found")
            return False
        
        # The config loaded by load_config_with_decryption already decrypts shioaji stuff, 
        # but we need to handle telegram decryption here if it wasn't done there.
        # Actually, load_config_with_decryption only decrypts shioaji section in the original code.
        # We should probably update it to decrypt everything or handle it here.
        
        bot_token = decrypt_config_value(config['telegram'].get('bot-token'), password)
        chat_id = decrypt_config_value(config['telegram'].get('chat-id'), password)
        
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
