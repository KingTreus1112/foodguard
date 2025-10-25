package com.example.foodguard3

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)



        auth = FirebaseAuth.getInstance()

        // If already logged in → go Home
        if (auth.currentUser != null) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        // Edge-to-edge padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val s = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(s.left, s.top, s.right, s.bottom)
            insets
        }

        val emailLayout = findViewById<TextInputLayout>(R.id.emailLayout)
        val passwordLayout = findViewById<TextInputLayout>(R.id.passwordLayout)
        val email = findViewById<TextInputEditText>(R.id.inputEmail)
        val password = findViewById<TextInputEditText>(R.id.inputPassword)
        val btnLogin = findViewById<MaterialButton>(R.id.btnLogin)
        val btnSignup = findViewById<MaterialButton>(R.id.btnSignup)
        val txtForgot = findViewById<TextView>(R.id.txtForgot)

        btnSignup.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnLogin.setOnClickListener {
            val em = email.text?.toString()?.trim().orEmpty()
            val pw = password.text?.toString()?.trim().orEmpty()

            // Clear previous errors
            emailLayout.error = null
            passwordLayout.error = null

            // Friendly validation to prevent “malformed credential”
            if (!Patterns.EMAIL_ADDRESS.matcher(em).matches()) {
                emailLayout.error = "invalid email format"
                return@setOnClickListener
            }
            if (pw.length < 6) {
                passwordLayout.error = "password must be 6+ characters"
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            auth.signInWithEmailAndPassword(em, pw)
                .addOnCompleteListener(this) { task ->
                    btnLogin.isEnabled = true
                    if (task.isSuccessful) {
                        startActivity(Intent(this, HomeActivity::class.java))
                        finish()
                    } else {
                        val msg = mapAuthError(task.exception)
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

                        // Show field-level hints if applicable
                        when (task.exception) {
                            is FirebaseAuthInvalidCredentialsException -> {
                                // invalid email OR wrong password
                                if ((task.exception as FirebaseAuthInvalidCredentialsException).errorCode == "ERROR_INVALID_EMAIL") {
                                    emailLayout.error = "invalid email format"
                                } else {
                                    passwordLayout.error = "wrong password"
                                }
                            }
                            is FirebaseAuthInvalidUserException -> {
                                emailLayout.error = "no account with that email"
                            }
                        }
                    }
                }
        }

        txtForgot.setOnClickListener {
            val em = email.text?.toString()?.trim().orEmpty()
            if (!Patterns.EMAIL_ADDRESS.matcher(em).matches()) {
                emailLayout.error = "enter a valid email first"
            } else {
                auth.sendPasswordResetEmail(em)
                    .addOnSuccessListener {
                        Toast.makeText(this, "reset email sent", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, e.localizedMessage ?: "could not send reset email", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun mapAuthError(e: Exception?): String {
        return when (e) {
            is FirebaseAuthInvalidCredentialsException -> {
                when (e.errorCode) {
                    "ERROR_INVALID_EMAIL" -> "email is badly formatted"
                    "ERROR_WRONG_PASSWORD" -> "wrong password"
                    else -> "invalid credentials"
                }
            }
            is FirebaseAuthInvalidUserException -> {
                when (e.errorCode) {
                    "ERROR_USER_NOT_FOUND" -> "no account with that email"
                    "ERROR_USER_DISABLED" -> "this account is disabled"
                    else -> "couldn’t log in with that account"
                }
            }
            is FirebaseNetworkException -> "no internet connection"
            else -> e?.localizedMessage ?: "login failed"
        }
    }
}
