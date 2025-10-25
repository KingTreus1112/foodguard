package com.example.foodguard3

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddFoodBottomSheet : BottomSheetDialogFragment() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    // edit mode args
    private var argId: String? = null
    private var argName: String? = null
    private var argCategory: String? = null
    private var argExpiryMillis: Long? = null

    private lateinit var inputName: TextInputEditText
    private lateinit var inputCategory: MaterialAutoCompleteTextView
    private lateinit var inputExpiry: TextInputEditText
    private lateinit var btnSave: MaterialButton

    private var chosenDate: Calendar? = null
    private val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            argId = it.getString("id")
            argName = it.getString("name")
            argCategory = it.getString("category")
            argExpiryMillis = it.getLong("expiryMillis", -1).takeIf { v -> v > 0 }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_add_food, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        inputName = view.findViewById(R.id.inputName)
        inputCategory = view.findViewById(R.id.inputCategory)
        inputExpiry = view.findViewById(R.id.inputExpiry)
        btnSave = view.findViewById(R.id.btnSave)

        // --- Category dropdown setup ---
        val cats = resources.getStringArray(R.array.food_categories).toList()
        inputCategory.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, cats)
        )
        // make it open on tap/focus
        inputCategory.setOnClickListener { inputCategory.showDropDown() }
        inputCategory.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) inputCategory.showDropDown()
        }

        // --- Prefill when editing ---
        argName?.let { inputName.setText(it) }
        argCategory?.let { inputCategory.setText(it, false) }
        argExpiryMillis?.let {
            chosenDate = Calendar.getInstance().apply { timeInMillis = it }
            inputExpiry.setText(fmt.format(Date(it)))
        }

        // --- Date picker ---
        inputExpiry.setOnClickListener {
            val cal = chosenDate ?: Calendar.getInstance()
            DatePickerDialog(
                requireContext(),
                { _, y, m, d ->
                    chosenDate = Calendar.getInstance().apply {
                        set(Calendar.YEAR, y)
                        set(Calendar.MONTH, m)
                        set(Calendar.DAY_OF_MONTH, d)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    inputExpiry.setText(fmt.format(chosenDate!!.time))
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        btnSave.setOnClickListener { save() }
    }

    private fun save() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(requireContext(), "Not signed in", Toast.LENGTH_SHORT).show()
            return
        }

        val name = inputName.text?.toString()?.trim().orEmpty()
        val category = inputCategory.text?.toString()?.trim().orEmpty().ifEmpty { "Others" }
        val expiryTs = chosenDate?.let { Timestamp(it.time) }

        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "Name is required", Toast.LENGTH_SHORT).show()
            return
        }

        val foodsCol = db.collection("users").document(uid).collection("foods")
        val now = Timestamp(Date())

        val data = mutableMapOf<String, Any>(
            "name" to name,
            "category" to category,
            "status" to "active",
            "updatedAt" to now
        )
        if (expiryTs != null) data["expiry"] = expiryTs

        val editId = argId
        val task = if (editId == null) {
            data["createdAt"] = now
            foodsCol.add(data)
        } else {
            foodsCol.document(editId).set(data, SetOptions.merge())
        }

        task.addOnSuccessListener { dismiss() }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Save failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    companion object {
        fun newInstance(food: FoodItem?): AddFoodBottomSheet {
            val f = AddFoodBottomSheet()
            if (food != null) {
                f.arguments = Bundle().apply {
                    putString("id", food.id)
                    putString("name", food.name)
                    putString("category", food.category)
                    putLong("expiryMillis", food.expiry?.toDate()?.time ?: -1L)
                }
            }
            return f
        }
    }
}
