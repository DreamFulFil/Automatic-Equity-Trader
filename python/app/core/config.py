import os
import yaml
import re
import base64
import hashlib
import requests
from Crypto.Cipher import DES

# ============================================================================
# JASYPT DECRYPTION
# ============================================================================

def jasypt_decrypt(encrypted_value: str, password: str) -> str:
    """Decrypt Jasypt PBEWithMD5AndDES encrypted value"""
    encrypted_bytes = base64.b64decode(encrypted_value)
    salt = encrypted_bytes[:8]
    ciphertext = encrypted_bytes[8:]
    
    password_bytes = password.encode('utf-8')
    key_material = password_bytes + salt
    
    for _ in range(1000):
        key_material = hashlib.md5(key_material).digest()
    
    key = key_material[:8]
    iv = key_material[8:16]
    
    cipher = DES.new(key, DES.MODE_CBC, iv)
    decrypted = cipher.decrypt(ciphertext)
    
    pad_len = decrypted[-1]
    if isinstance(pad_len, int) and 1 <= pad_len <= 8:
        return decrypted[:-pad_len].decode('utf-8')
    return decrypted.decode('utf-8')

def decrypt_config_value(value, password: str):
    """Decrypt value if it's ENC() wrapped, otherwise return as-is"""
    if isinstance(value, str):
        match = re.match(r'^ENC\((.+)\)$', value)
        if match:
            return jasypt_decrypt(match.group(1), password)
    return value

def load_config_with_decryption(password: str):
    """Load application.yml and decrypt ENC() values, and fetch dynamic settings from Java"""
    # Assuming this is run from python/ directory or similar, adjust path as needed
    # The original code used __file__ relative path.
    # We will assume the app is run from the 'python' directory or we can find the project root.
    
    # Try to find project root
    current_dir = os.getcwd()
    project_root = None
    
    # Walk up until we find src/main/resources/application.yml
    d = current_dir
    while d != "/":
        if os.path.exists(os.path.join(d, 'src/main/resources/application.yml')):
            project_root = d
            break
        d = os.path.dirname(d)
    
    if not project_root:
        # Fallback to relative path from this file if possible, or just assume standard layout
        # If we are in python/app/core, project root is ../../../
        script_dir = os.path.dirname(os.path.abspath(__file__))
        project_root = os.path.abspath(os.path.join(script_dir, '../../../'))

    config_path = os.path.join(project_root, 'src/main/resources/application.yml')
    
    with open(config_path, 'r') as f:
        config = yaml.safe_load(f)
    
    if 'shioaji' in config:
        # Decrypt common fields
        for key in ['ca-password', 'person-id']:
            if key in config['shioaji']:
                config['shioaji'][key] = decrypt_config_value(config['shioaji'][key], password)
        
        # Decrypt stock keys
        if 'stock' in config['shioaji']:
             for key in ['api-key', 'secret-key']:
                if key in config['shioaji']['stock']:
                    config['shioaji']['stock'][key] = decrypt_config_value(config['shioaji']['stock'][key], password)

        # Decrypt future keys
        if 'future' in config['shioaji']:
             for key in ['api-key', 'secret-key']:
                if key in config['shioaji']['future']:
                    config['shioaji']['future'][key] = decrypt_config_value(config['shioaji']['future'][key], password)
        
        ca_path = config['shioaji'].get('ca-path', 'Sinopac.pfx')
        if not os.path.isabs(ca_path):
            ca_path = os.path.join(project_root, ca_path)
        config['shioaji']['ca-path'] = os.path.abspath(ca_path)
        
        print(f"✅ CA certificate path: {config['shioaji']['ca-path']}")
        
        # Fetch dynamic simulation setting from Java
        try:
            response = requests.get('http://localhost:16350/api/shioaji/settings', timeout=5)
            if response.status_code == 200:
                java_settings = response.json()
                config['shioaji']['simulation'] = java_settings.get('simulation', True)
                print(f"✅ Simulation mode from Java: {config['shioaji']['simulation']}")
            else:
                # Fallback to existing config or default True
                if 'simulation' not in config['shioaji']:
                    config['shioaji']['simulation'] = True
                print(f"⚠️ Failed to fetch simulation from Java, using config default: {config['shioaji']['simulation']}")
        except Exception as e:
            # Fallback to existing config or default True
            if 'simulation' not in config['shioaji']:
                config['shioaji']['simulation'] = True
            print(f"⚠️ Error fetching simulation from Java: {e}, using config default: {config['shioaji']['simulation']}")
    
    return config
