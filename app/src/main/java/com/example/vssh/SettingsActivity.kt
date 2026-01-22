package com.example.vssh

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    private lateinit var apiKeyInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var baseUrlInput: EditText
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        apiKeyInput = findViewById(R.id.apiKeyInput)
        modelInput = findViewById(R.id.modelInput)
        baseUrlInput = findViewById(R.id.baseUrlInput)
        saveButton = findViewById(R.id.saveButton)

        val store = SettingsStore(this)
        apiKeyInput.setText(store.getApiKey())
        modelInput.setText(store.getModel())
        baseUrlInput.setText(store.getBaseUrl())

        saveButton.setOnClickListener {
            store.setApiKey(apiKeyInput.text.toString())
            store.setModel(modelInput.text.toString())
            store.setBaseUrl(baseUrlInput.text.toString())
            Toast.makeText(this, "OpenRouter Settings gespeichert", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
