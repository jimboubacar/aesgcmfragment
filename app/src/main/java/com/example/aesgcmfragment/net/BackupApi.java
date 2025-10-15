package com.example.aesgcmfragment.net;

public interface BackupApi {
    byte[] fetchServerPublicKey(); // X.509 DER bytes for RSA key (keyVersion implied or separate)
    void uploadBackup(String userId, String keyVersion, String ivB64, String ctB64, String wrappedDekB64, String aadB64);
    // For restore: server unwraps DEK and returns raw DEK bytes securely to this device/session
    byte[] requestDekUnwrap(String userId, String keyVersion, String wrappedDekB64);
}

