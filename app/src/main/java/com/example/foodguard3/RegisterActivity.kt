package com.example.foodguard3

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val firstName = findViewById<TextInputEditText>(R.id.inputFirstName)
        val email = findViewById<TextInputEditText>(R.id.inputEmailReg)
        val password = findViewById<TextInputEditText>(R.id.inputPasswordReg)

        val emailLayout = findViewById<TextInputLayout>(R.id.emailLayout) ?: null
        val passwordLayout = findViewById<TextInputLayout>(R.id.passwordLayout) ?: null
        // (Those are only present on the login layout; safe if null here.)

        val btnCreate = findViewById<MaterialButton>(R.id.btnCreateAccount)
        val txtGoLogin = findViewById<TextView>(R.id.txtGoLogin)

        txtGoLogin.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        btnCreate.setOnClickListener {
            val fn = firstName.text?.toString()?.trim().orEmpty()
            val em = email.text?.toString()?.trim().orEmpty()
            val pw = password.text?.toString()?.trim().orEmpty()

            if (fn.isEmpty()) {
                Toast.makeText(this, "please enter first name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(em).matches()) {
                Toast.makeText(this, "invalid email format", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (pw.length < 6) {
                Toast.makeText(this, "password must be 6+ characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnCreate.isEnabled = false

            auth.createUserWithEmailAndPassword(em, pw)
                .addOnSuccessListener {
                    val uid = auth.currentUser!!.uid
                    val profile = mapOf(
                        "firstName" to fn,
                        "email" to em,
                        "createdAt" to System.currentTimeMillis()
                    )

                    db.collection("users").document(uid).set(profile)
                        .addOnSuccessListener {
                            Toast.makeText(this, "account created", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, HomeActivity::class.java))
                            finish()
                        }
                        .addOnFailureListener { e ->
                            btnCreate.isEnabled = true
                            Toast.makeText(this, "profile save failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
                .addOnFailureListener { e ->
                    btnCreate.isEnabled = true
                    val msg = when (e) {
                        is FirebaseAuthUserCollisionException -> "email is already in use"
                        is FirebaseNetworkException -> "no internet connection"
                        else -> e.localizedMessage ?: "sign up failed"
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
        }
    }
}
