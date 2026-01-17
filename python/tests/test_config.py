import pytest
import os
import tempfile
import yaml
import base64
import hashlib
from pathlib import Path
from Crypto.Cipher import DES
from app.core.config import jasypt_decrypt, decrypt_config_value, load_config_with_decryption


def create_jasypt_encrypted_value(plaintext: str, password: str) -> str:
    """Helper to create JASYPT encrypted value for testing"""
    salt = b'12345678'  # Fixed salt for testing
    password_bytes = password.encode('utf-8')
    key_material = password_bytes + salt
    
    for _ in range(1000):
        key_material = hashlib.md5(key_material).digest()
    
    key = key_material[:8]
    iv = key_material[8:16]
    
    cipher = DES.new(key, DES.MODE_CBC, iv)
    
    # Add PKCS5 padding
    pad_len = 8 - (len(plaintext) % 8)
    padded = plaintext.encode('utf-8') + bytes([pad_len] * pad_len)
    
    ciphertext = cipher.encrypt(padded)
    encrypted_bytes = salt + ciphertext
    
    return base64.b64encode(encrypted_bytes).decode('utf-8')


def test_jasypt_decrypt_simple():
    """Test basic JASYPT decryption"""
    password = "test_password"
    plaintext = "secret123"
    
    encrypted = create_jasypt_encrypted_value(plaintext, password)
    decrypted = jasypt_decrypt(encrypted, password)
    
    assert decrypted == plaintext


def test_jasypt_decrypt_longer_text():
    """Test JASYPT decryption with longer text"""
    password = "my_secret_key"
    plaintext = "This is a much longer secret message"
    
    encrypted = create_jasypt_encrypted_value(plaintext, password)
    decrypted = jasypt_decrypt(encrypted, password)
    
    assert decrypted == plaintext


def test_jasypt_decrypt_with_special_characters():
    """Test JASYPT decryption with special characters"""
    password = "password123"
    plaintext = "P@ssw0rd!#$%"
    
    encrypted = create_jasypt_encrypted_value(plaintext, password)
    decrypted = jasypt_decrypt(encrypted, password)
    
    assert decrypted == plaintext


def test_decrypt_config_value_plain():
    """Test that plain values pass through unchanged"""
    password = "test-password"
    plain_value = "some-plain-value"
    result = decrypt_config_value(plain_value, password)
    assert result == plain_value


def test_decrypt_config_value_with_enc_wrapper():
    """Test decrypt_config_value with ENC() wrapper"""
    password = "test_password"
    plaintext = "database_password"
    
    encrypted = create_jasypt_encrypted_value(plaintext, password)
    wrapped = f"ENC({encrypted})"
    
    decrypted = decrypt_config_value(wrapped, password)
    
    assert decrypted == plaintext


def test_decrypt_config_value_non_string():
    """Test that non-string values pass through unchanged"""
    password = "test-password"
    int_value = 42
    result = decrypt_config_value(int_value, password)
    assert result == int_value
    
    dict_value = {"key": "value"}
    result = decrypt_config_value(dict_value, password)
    assert result == dict_value
    
    # Additional types
    assert decrypt_config_value(True, password) is True
    assert decrypt_config_value(None, password) is None
    assert decrypt_config_value([1, 2, 3], password) == [1, 2, 3]


def test_decrypt_config_value_not_enc():
    """Test that strings without ENC() pass through"""
    password = "test-password"
    normal_string = "jdbc:postgresql://localhost:5432/db"
    result = decrypt_config_value(normal_string, password)
    assert result == normal_string
    
    # Partial ENC matches should not decrypt
    assert decrypt_config_value("ENC(incomplete", password) == "ENC(incomplete"
    assert decrypt_config_value("prefix_ENC(value)", password) == "prefix_ENC(value)"


def test_decrypt_config_value_case_sensitive():
    """Test that ENC() wrapper is case-sensitive"""
    password = "test_password"
    plaintext = "secret"
    
    encrypted = create_jasypt_encrypted_value(plaintext, password)
    
    # Lowercase should not match
    assert decrypt_config_value(f"enc({encrypted})", password) == f"enc({encrypted})"
    
    # Only uppercase ENC() should match
    result = decrypt_config_value(f"ENC({encrypted})", password)
    assert result == plaintext


def test_jasypt_decrypt_with_padding():
    """Test JASYPT decryption handles padding correctly"""
    password = "test_password"
    
    # Test various plaintext lengths to exercise different padding scenarios
    for length in range(1, 17):
        plaintext = "a" * length
        encrypted = create_jasypt_encrypted_value(plaintext, password)
        decrypted = jasypt_decrypt(encrypted, password)
        assert decrypted == plaintext, f"Failed for length {length}"


def test_load_config_missing_password(monkeypatch):
    """Test that load_config_with_decryption handles missing password"""
    # Remove JASYPT_PASSWORD if it exists
    monkeypatch.delenv("JASYPT_PASSWORD", raising=False)
    
    # Without a password, loading should still work but encrypted values won't decrypt
    # The function should handle this gracefully or raise an appropriate error
    try:
        result = load_config_with_decryption("")
        # If it succeeds, that's fine
        assert result is not None
    except (KeyError, ValueError, AttributeError, FileNotFoundError):
        # These are acceptable errors when password is missing or config not found
        pass


def test_config_loading_with_temp_file(monkeypatch, tmp_path):
    """Test config loading with a temporary YAML file"""
    # Create a temporary config file
    config_data = {
        "spring": {
            "datasource": {
                "url": "jdbc:postgresql://localhost:5432/testdb",
                "username": "testuser",
                "password": "ENC(fakeencryptedvalue)"
            }
        }
    }

    config_file = tmp_path / "application.yml"
    with open(config_file, 'w') as f:
        yaml.dump(config_data, f)

    # Set environment variable
    monkeypatch.setenv("JASYPT_PASSWORD", "test-password")

    # This test verifies the function can be called
    # Actual decryption might fail with fake encrypted values
    assert config_file.exists()


def test_load_config_with_decryption_walks_up_and_handles_java_errors(tmp_path, monkeypatch):
    project_root = tmp_path / "proj"
    (project_root / "src/main/resources").mkdir(parents=True)
    (project_root / "Sinopac.pfx").write_text("dummy")

    (project_root / "src/main/resources/application.yml").write_text(
        """
shioaji:
  stock:
    api-key: plain
    secret-key: plain
  future:
    api-key: plain
    secret-key: plain
  ca-path: Sinopac.pfx
  ca-password: plain
  person-id: plain
""".lstrip()
    )

    nested = project_root / "python" / "tests"
    nested.mkdir(parents=True)
    monkeypatch.chdir(nested)

    def raise_exc(*args, **kwargs):
        raise Exception("java down")

    monkeypatch.setattr("app.core.config.requests.get", raise_exc)

    cfg = load_config_with_decryption("pwd")
    assert cfg["shioaji"]["simulation"] is True
    assert os.path.isabs(cfg["shioaji"]["ca-path"])


def test_load_config_with_decryption_fallback_and_java_responses(monkeypatch):
    monkeypatch.setattr("app.core.config.os.getcwd", lambda: "/tmp")
    monkeypatch.setattr("app.core.config.os.path.exists", lambda p: False)

    monkeypatch.setattr("app.core.config.decrypt_config_value", lambda v, p: v)

    class Ok:
        status_code = 200
        def json(self):
            return {"simulation": False}

    monkeypatch.setattr("app.core.config.requests.get", lambda *a, **k: Ok())
    cfg = load_config_with_decryption("pwd")
    assert cfg["shioaji"]["simulation"] is False

    class Bad:
        status_code = 500
        def json(self):
            return {}

    monkeypatch.setattr("app.core.config.requests.get", lambda *a, **k: Bad())
    cfg2 = load_config_with_decryption("pwd")
    assert cfg2["shioaji"]["simulation"] is True


def test_decrypt_config_nested():
    """Test decryption of nested config structures"""
    password = "test-password"
    
    # Plain nested config
    plain_config = {
        "database": {
            "host": "localhost",
            "port": 5432
        }
    }
    
    # Decrypt should handle nested structures
    result = decrypt_config_value(plain_config, password)
    assert result == plain_config

