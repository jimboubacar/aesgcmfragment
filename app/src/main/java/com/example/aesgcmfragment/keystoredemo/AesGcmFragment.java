package com.example.aesgcmfragment.keystoredemo; // Package name for this fragment class

// Static imports for KeyProperties constants to keep code concise below
import static android.security.keystore.KeyProperties.BLOCK_MODE_GCM;            // Use GCM block mode
import static android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE;   // GCM uses no padding
import static android.security.keystore.KeyProperties.KEY_ALGORITHM_AES;         // AES algorithm
import static android.security.keystore.KeyProperties.PURPOSE_DECRYPT;           // Key purpose: decrypt
import static android.security.keystore.KeyProperties.PURPOSE_ENCRYPT;           // Key purpose: encrypt

import android.annotation.SuppressLint;      // For localised lint suppression
import android.content.Context;              // Android context handle
import android.content.SharedPreferences;    // Simple key–value persistence
import android.os.Bundle;                    // Saved instance state bundle
import android.os.Handler;                   // Post work back to main thread
import android.os.Looper;                    // Main thread looper
import android.util.Base64;                  // Base64 encode/decode helpers
import android.view.LayoutInflater;          // Inflate XML layouts
import android.view.View;                    // Base class for UI widgets
import android.view.ViewGroup;               // Parent view container
import android.widget.Button;                // Button widget
import android.widget.EditText;              // Text input field
import android.widget.TextView;              // Text display widget

import androidx.annotation.NonNull;          // Nullability annotations
import androidx.annotation.Nullable;         // Nullability annotations
import androidx.fragment.app.Fragment;       // AndroidX Fragment base class

import com.example.aesgcmfragment.R;         // Generated resources (layouts, strings, etc.)

import java.security.Key;                    // Generic key type
import java.security.KeyStore;               // AndroidKeyStore access
import java.text.MessageFormat;              // Safe string formatting
import java.util.concurrent.ExecutorService; // Background thread pool interface
import java.util.concurrent.Executors;       // Factory for executors
import java.util.concurrent.atomic.AtomicBoolean; // Thread-safe flag for debouncing

import javax.crypto.Cipher;                  // JCA cipher API
import javax.crypto.KeyGenerator;            // Key generator for AES keys
import javax.crypto.SecretKey;               // AES secret key type
import javax.crypto.spec.GCMParameterSpec;   // Holds IV + tag length for GCM

public class AesGcmFragment extends Fragment { // Fragment implementing AES-GCM demo UI/logic

    private static final String KEY_ALIAS = "pii_key"; // Alias used to store/fetch key in AndroidKeyStore
    private static final String PREFS = "crypto_demo"; // SharedPreferences file name
    private static final String PREF_IV = "iv_b64";    // Key for persisted IV (Base64)
    private static final String PREF_CT = "ct_b64";    // Key for persisted ciphertext (Base64)
    private static final int MAX_INPUT_BYTES = 256 * 1024; // 256 KiB demo cap to avoid long work on slow devices

    private byte[] lastIv, lastCt;                     // In-memory copies of the most recent IV and ciphertext

    private final ExecutorService cryptoExec = Executors.newSingleThreadExecutor(); // Serial background worker for crypto/I/O
    private final Handler main = new Handler(Looper.getMainLooper());               // Handler to post results back to UI thread
    private final AtomicBoolean busy = new AtomicBoolean(false);                    // Debounce flag to prevent overlapping jobs

    @SuppressLint("DefaultLocale") // We control formatting; suppress default locale lint for String.format usage
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        View v = inf.inflate(R.layout.fragment_aes_gcm, c, false); // Inflate the fragment’s layout XML
        EditText input = v.findViewById(R.id.inputText);           // Text box for plaintext entry
        TextView out = v.findViewById(R.id.output);                // Output area for IV/CT/plaintext/errors

        Button btnEncrypt = v.findViewById(R.id.btnEncrypt);       // “Encrypt” button
        Button btnDecrypt = v.findViewById(R.id.btnDecrypt);       // “Decrypt” button

        btnEncrypt.setOnClickListener(b -> {                       // Handle encrypt clicks
            if (!busy.compareAndSet(false, true)) return;          // If already working, ignore (debounce)
            out.setText(R.string.encrypting);                      // Tell the user we’ve started

            final byte[] ptBytes = input.getText().toString()      // Read plaintext from UI
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8); // Encode as UTF-8 bytes
            if (ptBytes.length > MAX_INPUT_BYTES) {                // Guard against very large inputs
                busy.set(false);                                   // Clear busy flag
                out.setText(String.format(                         // Show a friendly size error
                        "Input too large (%d bytes). Limit is ~%d bytes for this demo.",
                        ptBytes.length, MAX_INPUT_BYTES));
                return;                                            // Abort this run
            }

            cryptoExec.execute(() -> {                              // Do crypto + persistence off the UI thread
                try {
                    SecretKey key = ensureKey();                    // Fetch or create the AES key in AndroidKeyStore

                    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); // Configure AES-GCM with no padding
                    // IMPORTANT: Do NOT supply an IV when encrypting with randomized encryption required.
                    cipher.init(Cipher.ENCRYPT_MODE, key);          // Initialises for encrypt; Keystore enforces random IV

                    // Optional AAD binds context (must be repeated on decrypt if used):
                    // cipher.updateAAD(CryptoUtils.utf8("user123"));

                    byte[] ct = cipher.doFinal(ptBytes);            // Encrypts and appends 128-bit tag to the output
                    byte[] iv = cipher.getIV();                     // Retrieve the fresh, random 12-byte IV used

                    // Persist results (still off UI thread) so we can decrypt later even after process death
                    SharedPreferences prefs = requireContext()
                            .getSharedPreferences(PREFS, Context.MODE_PRIVATE); // Open/create our prefs file
                    prefs.edit()
                            .putString(PREF_IV, CryptoUtils.b64(iv)) // Store IV as Base64
                            .putString(PREF_CT, CryptoUtils.b64(ct)) // Store ciphertext+tag as Base64
                            .apply();                                // Apply asynchronously

                    lastIv = iv;                                     // Keep in memory for quick decrypt
                    lastCt = ct;                                     // Keep in memory for quick decrypt

                    postToUi(() -> {                                 // Back to UI thread to update the screen
                        out.setText(                                  // Show IV and ciphertext (Base64)
                                MessageFormat.format("{0}\n{1}",
                                        getString(R.string.iv_b64, CryptoUtils.b64(iv)),
                                        getString(R.string.ct_b64, CryptoUtils.b64(ct)))
                        );
                    });
                } catch (Exception e) {                              // Any exception during encrypt/persist
                    postToUi(() -> out.setText(                      // Show a user-friendly message with details
                            getString(R.string.error_generic, e.toString())));
                } finally {
                    busy.set(false);                                 // Always release the debounce flag
                }
            });
        });

        btnDecrypt.setOnClickListener(b -> {                         // Handle decrypt clicks
            if (!busy.compareAndSet(false, true)) return;            // Debounce concurrent presses
            out.setText(R.string.decrypting);                        // Inform the user we’ve started

            cryptoExec.execute(() -> {                               // Do work off the UI thread
                try {
                    // If in-memory copies are missing, try to restore from SharedPreferences
                    if (lastIv == null || lastCt == null) {
                        SharedPreferences prefs = requireContext()
                                .getSharedPreferences(PREFS, Context.MODE_PRIVATE); // Open prefs
                        String ivB64 = prefs.getString(PREF_IV, null);              // Read stored IV
                        String ctB64 = prefs.getString(PREF_CT, null);              // Read stored CT
                        if (ivB64 != null && ctB64 != null) {
                            lastIv = Base64.decode(ivB64, Base64.DEFAULT);          // Decode IV from Base64
                            lastCt = Base64.decode(ctB64, Base64.DEFAULT);          // Decode CT from Base64
                        }
                    }

                    if (lastIv == null || lastCt == null) {          // Nothing to decrypt yet
                        postToUi(() -> out.setText(R.string.encrypt_first)); // Prompt user to encrypt first
                        return;                                       // Abort
                    }

                    SecretKey key = ensureKey();                     // Retrieve the same Keystore key
                    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); // Configure cipher again
                    // On decrypt, you MUST supply the IV and the same tag length used for encrypt.
                    cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, lastIv)); // 128-bit tag, same IV
                    // If you used AAD during encrypt, you must provide identical bytes here:
                    // cipher.updateAAD(CryptoUtils.utf8("user123"));

                    byte[] pt = cipher.doFinal(lastCt);              // Decrypts and verifies tag; throws on auth failure
                    postToUi(() ->                                  // Back to UI to display plaintext
                            out.setText(getString(R.string.plain_text, new String(pt))));
                } catch (javax.crypto.AEADBadTagException e) {       // Tag verification failed – wrong key/IV/AAD or tampering
                    postToUi(() -> out.setText(getString(
                            R.string.decrypt_failed,
                            "Authentication failed (tampered data / wrong key or AAD)."
                    )));
                } catch (Exception e) {                               // Any other error during decrypt
                    postToUi(() -> out.setText(getString(
                            R.string.decrypt_failed, e.toString())));
                } finally {
                    busy.set(false);                                  // Release debounce flag
                }
            });
        });

        return v;                                                     // Return the root view for the fragment
    }

    private void postToUi(Runnable r) {           // Helper to post work back to the main thread safely
        if (isAdded()) main.post(r);              // Only post if fragment is attached to its activity
    }

    @Override
    public void onDestroy() {                     // Fragment is being destroyed
        super.onDestroy();                        // Call base implementation
        cryptoExec.shutdownNow();                 // Stop background executor to avoid leaks
    }

    private SecretKey ensureKey() throws Exception { // Create or fetch our AES key from AndroidKeyStore
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); // Get a handle to the platform keystore
        ks.load(null);                                         // Load default keystore
        Key key = ks.getKey(KEY_ALIAS, null);                  // Try to retrieve an existing key by alias
        if (key instanceof SecretKey) return (SecretKey) key;  // If present, return it

        KeyGenerator kg = KeyGenerator.getInstance(KEY_ALGORITHM_AES, "AndroidKeyStore"); // Build an AES key generator
        kg.init(new android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS, PURPOSE_ENCRYPT | PURPOSE_DECRYPT)  // Allow both encrypt and decrypt
                .setBlockModes(BLOCK_MODE_GCM)                 // Restrict to GCM mode
                .setEncryptionPaddings(ENCRYPTION_PADDING_NONE) // GCM requires no padding
                .setRandomizedEncryptionRequired(true)        // Enforce fresh random IV per encryption (no reuse)
                // .setKeySize(256)                            // Uncomment for 256-bit keys if device supports it
                .build());
        return kg.generateKey();                               // Create and store the new key, then return it
    }
}

/*
*
* Encrypt button (replace body):
* // Pseudocode inside your background executor
String userId = "<current user>";
byte[] aad = null; // or set context
SecretKey dek = DekCrypto.generateDek();
byte[] iv = DekCrypto.randomIv();
byte[] ct = DekCrypto.aeadEncrypt(dek, iv, plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8), aad);

// Get server pubkey + wrap DEK
String keyVersion = "rsa-2024-10"; // from API/metadata
byte[] serverPub = backupApi.fetchServerPublicKey();
byte[] wrappedDek = DekCrypto.wrapDekWithRsaOaep(serverPub, dek);

// Save locally for UX
prefs.edit()
    .putString("key_version", keyVersion)
    .putString("iv_b64", CryptoUtils.b64(iv))
    .putString("ct_b64", CryptoUtils.b64(ct))
    .putString("wrapped_dek_b64", CryptoUtils.b64(wrappedDek))
    .apply();

// Upload to backend for disaster recovery
backupApi.uploadBackup(userId, keyVersion,
        CryptoUtils.b64(iv), CryptoUtils.b64(ct), CryptoUtils.b64(wrappedDek),
        aad == null ? null : CryptoUtils.b64(aad));

*
*Decrypt button (replace body):
*
*
*
* */

/*
* */