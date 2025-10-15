package com.example.aesgcmfragment.crypto;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;

public final class DekCrypto {
    private static final SecureRandom RNG = new SecureRandom();

    public static SecretKey generateDek() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256);
        return kg.generateKey();
    }

    public static byte[] randomIv() {
        byte[] iv = new byte[12];
        RNG.nextBytes(iv);
        return iv;
    }

    public static byte[] aeadEncrypt(SecretKey dek, byte[] iv, byte[] pt, byte[] aad) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, dek, new GCMParameterSpec(128, iv));
        if (aad != null) c.updateAAD(aad);
        return c.doFinal(pt); // ct||tag
    }

    public static byte[] aeadDecrypt(SecretKey dek, byte[] iv, byte[] ct, byte[] aad) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, dek, new GCMParameterSpec(128, iv));
        if (aad != null) c.updateAAD(aad);
        return c.doFinal(ct);
    }

    public static byte[] wrapDekWithRsaOaep(byte[] serverPubKeyX509Der, SecretKey dek) throws Exception {
        PublicKey pub = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(serverPubKeyX509Der));
        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsa.init(Cipher.ENCRYPT_MODE, pub);
        return rsa.doFinal(dek.getEncoded());
    }
}
