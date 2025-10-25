package com.example.foodguard3

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class ProfileFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var inputEmailReadOnly: EditText? = null
    private var inputFirstName: EditText? = null
    private var inputReminderDays: EditText? = null
    private var btnSave: MaterialButton? = null
    private var btnSignOut: MaterialButton? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        inputEmailReadOnly = view.findViewById(R.id.inputEmailReadOnly)
        inputFirstName = view.findViewById(R.id.inputFirstName)
        inputReminderDays = view.findViewById(R.id.inputReminderDays)
        btnSave = view.findViewById(R.id.btnSaveProfile)
        btnSignOut = view.findViewById(R.id.btnSignOut)

        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "Not signed in", Toast.LENGTH_SHORT).show()
            return
        }

        // show email
        inputEmailReadOnly?.setText(user.email ?: "")

        // load profile fields from Firestore
        val docRef = db.collection("users").document(user.uid)
        docRef.get().addOnSuccessListener { doc ->
            val first = doc.getString("firstName").orEmpty()
            val days = (doc.getLong("reminderDays") ?: 3L).toInt()

            inputFirstName?.setText(first)
            inputReminderDays?.setText(days.toString())
        }

        btnSave?.setOnClickListener {
            val first = inputFirstName?.text?.toString()?.trim().orEmpty()
            val days = inputReminderDays?.text?.toString()?.trim()?.toIntOrNull() ?: 3

            val data = hashMapOf(
                "firstName" to first,
                "reminderDays" to days
            )
            docRef.set(data, SetOptions.merge())
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Saved", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Save failed: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }

        btnSignOut?.setOnClickListener {
            auth.signOut()
            // back to login
            val intent = Intent(requireContext(), MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        inputEmailReadOnly = null
        inputFirstName = null
        inputReminderDays = null
        btnSave = null
        btnSignOut = null
    }
}
