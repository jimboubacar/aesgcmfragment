package com.example.aesgcmfragment;                        // App package containing the activity

import android.os.Bundle;                                  // For saved instance state passed to onCreate
import androidx.appcompat.app.AppCompatActivity;           // Base class for activities using the AppCompat theme
import com.example.aesgcmfragment.databinding.ActivityMainBinding; // Generated ViewBinding class for activity_main.xml
import com.example.aesgcmfragment.keystoredemo.AesGcmFragment;     // The fragment we’ll host in this activity

public class MainActivity extends AppCompatActivity {      // Single-activity host for the AES-GCM demo

    @Override protected void onCreate(Bundle savedInstanceState) { // Entry point when the activity is created
        super.onCreate(savedInstanceState);                          // Let the superclass do its initial setup
        // Strongly-typed binding to the activity’s layout
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());  // Inflate the XML layout via ViewBinding
        setContentView(binding.getRoot());                           // Set the inflated root view as the content view

        if (savedInstanceState == null) {                            // Only add the fragment on first creation (not on rotation)
            getSupportFragmentManager()                              // Get FragmentManager for transactions
                    .beginTransaction()                              // Begin a new fragment transaction
                    .replace(binding.fragmentContainer.getId(),      // Replace the container view (from layout)
                            new AesGcmFragment())                   // …with a fresh instance of our AES-GCM fragment
                    .commit();                                       // Commit the transaction immediately
        }
    }
}
