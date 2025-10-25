package com.example.foodguard3

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class PantryFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var chips: ChipGroup
    private lateinit var search: EditText
    private lateinit var list: ListView
    private lateinit var empty: TextView

    private val allFoods = mutableListOf<FoodItem>()
    private val visibleFoods = mutableListOf<FoodItem>()
    private lateinit var adapter: ArrayAdapter<FoodItem>

    private val dateFmt by lazy { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    private var selectedCategory: String? = null
    private var searchText: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_pantry, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        chips = view.findViewById(R.id.chipsCategories)
        search = view.findViewById(R.id.inputSearch)
        list = view.findViewById(R.id.listFoods)
        empty = view.findViewById(R.id.txtEmpty)

        adapter = object : ArrayAdapter<FoodItem>(requireContext(), 0, visibleFoods) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView ?: layoutInflater.inflate(R.layout.item_food, parent, false)
                val item = getItem(position)!!

                row.findViewById<TextView>(R.id.txtName).text = item.name
                val meta = buildString {
                    append(item.category)
                    item.expiry?.let { append(" • ${dateFmt.format(it.toDate())}") }
                }
                row.findViewById<TextView>(R.id.txtMeta).text = meta

                return row
            }
        }
        list.adapter = adapter

        // TAP = edit
        list.setOnItemClickListener { _, _, pos, _ ->
            val food = visibleFoods[pos]
            AddFoodBottomSheet.newInstance(food)
                .show(parentFragmentManager, "editFood")
        }

        // LONG-PRESS = delete (soft delete with Undo)
        list.setOnItemLongClickListener { _, _, pos, _ ->
            val food = visibleFoods[pos]
            AlertDialog.Builder(requireContext())
                .setTitle("Delete ${food.name}?")
                .setMessage("This will remove it from your pantry.")
                .setPositiveButton("Delete") { _, _ -> deleteFood(food) }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }

        addCategoryChips()

        chips.setOnCheckedStateChangeListener { _, _ ->
            selectedCategory = chips.checkedChipId
                .takeIf { it != View.NO_ID }
                ?.let { id -> view.findViewById<Chip>(id).text.toString() }
                ?.takeIf { it != "All" }
            applyFilters()
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchText = s?.toString()?.trim().orEmpty()
                applyFilters()
            }
        })

        listenFoods()
    }

    private fun addCategoryChips() {
        val cats = resources.getStringArray(R.array.food_categories)

        fun makeChip(label: String): Chip =
            Chip(requireContext()).apply {
                text = label
                isCheckable = true
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            }

        chips.removeAllViews()
        chips.addView(makeChip("All"))
        cats.forEach { chips.addView(makeChip(it)) }
        (chips.getChildAt(0) as? Chip)?.isChecked = true
    }

    private fun listenFoods() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).collection("foods")
            .addSnapshotListener { snaps, _ ->
                allFoods.clear()
                snaps?.documents?.forEach { d ->
                    val status = d.getString("status") ?: "active"
                    val name = d.getString("name") ?: ""
                    val cat = d.getString("category") ?: "Others"
                    val expiry = d.getTimestamp("expiry")
                    allFoods.add(FoodItem(d.id, name, cat, expiry, status))
                }
                applyFilters()
            }
    }

    private fun applyFilters() {
        visibleFoods.clear()
        visibleFoods += allFoods
            .asSequence()
            .filter { it.status == "active" }
            .filter { selectedCategory == null || it.category == selectedCategory }
            .filter { searchText.isEmpty() || it.name.contains(searchText, true) }
            .sortedWith(compareBy(
                { it.expiry?.toDate()?.time ?: Long.MAX_VALUE },
                { it.name.lowercase(Locale.getDefault()) }
            ))
            .toList()

        adapter.notifyDataSetChanged()
        empty.visibility = if (visibleFoods.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun deleteFood(food: FoodItem) {
        val uid = auth.currentUser?.uid ?: return
        val doc = db.collection("users").document(uid)
            .collection("foods").document(food.id)

        doc.update(mapOf(
            "status" to "deleted",
            "updatedAt" to Timestamp(Date())
        )).addOnSuccessListener {
            Snackbar.make(requireView(), "Deleted ${food.name}", Snackbar.LENGTH_LONG)
                .setAction("Undo") {
                    doc.update(mapOf(
                        "status" to "active",
                        "updatedAt" to Timestamp(Date())
                    ))
                }
                .show()
        }
    }
}
