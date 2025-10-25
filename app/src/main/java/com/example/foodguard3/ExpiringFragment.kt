package com.example.foodguard3

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class ExpiringFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var chips: ChipGroup
    private lateinit var list: ListView
    private lateinit var empty: TextView

    private val allFoods = mutableListOf<FoodItem>()
    private val shownFoods = mutableListOf<FoodItem>()
    private lateinit var adapter: ArrayAdapter<FoodItem>

    private data class Window(val label: String, val days: Int)
    private val windows = listOf(
        Window("Overdue", -1),
        Window("Today", 0),
        Window("3 days", 3),
        Window("7 days", 7),
        Window("All", Int.MAX_VALUE)
    )
    private var selectedWindow: Window = windows[2]

    private val dateFmt by lazy { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_expiring, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        chips = view.findViewById(R.id.chipsWindow)
        list = view.findViewById(R.id.listExpiring)
        empty = view.findViewById(R.id.txtEmpty)

        adapter = object : ArrayAdapter<FoodItem>(requireContext(), 0, shownFoods) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView ?: layoutInflater.inflate(R.layout.item_expiring, parent, false)
                val item = getItem(position)!!

                val name = row.findViewById<TextView>(R.id.txtName)
                val meta = row.findViewById<TextView>(R.id.txtMeta)

                name.text = item.name
                val (metaText, tintColor) = metaLine(item)
                meta.text = metaText
                tintColor?.let { meta.setTextColor(it) }

                return row
            }
        }
        list.adapter = adapter

        // long press = delete (no edit in Expiring)
        list.setOnItemLongClickListener { _, _, pos, _ ->
            val food = shownFoods[pos]
            AlertDialog.Builder(requireContext())
                .setTitle("Delete ${food.name}?")
                .setMessage("This will remove it from your pantry.")
                .setPositiveButton("Delete") { _, _ -> deleteFood(food) }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }

        buildChips()
        listenFoods()
    }

    private fun buildChips() {
        chips.removeAllViews()
        windows.forEachIndexed { i, w ->
            val chip = Chip(requireContext()).apply {
                text = w.label
                isCheckable = true
            }
            chips.addView(chip)
            if (i == 2) chip.isChecked = true
        }
        chips.setOnCheckedStateChangeListener { group, _ ->
            val id = group.checkedChipId
            selectedWindow = if (id != View.NO_ID) {
                val chip = group.findViewById<Chip>(id)
                windows.first { it.label == chip.text.toString() }
            } else windows[2]
            applyWindow()
        }
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
                    if (expiry != null) {
                        allFoods.add(FoodItem(d.id, name, cat, expiry, status))
                    }
                }
                applyWindow()
            }
    }

    private fun startOfTodayMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun applyWindow() {
        val todayStart = startOfTodayMillis()
        val dayMs = 86_400_000L

        val overdueOnly = selectedWindow.days < 0
        val rangeEnd = if (selectedWindow.days == Int.MAX_VALUE) Long.MAX_VALUE
        else todayStart + (selectedWindow.days + 1) * dayMs - 1

        shownFoods.clear()
        shownFoods += allFoods
            .asSequence()
            .filter { it.status == "active" }
            .filter { it.expiry != null }
            .filter { food ->
                val exp = food.expiry!!.toDate().time
                if (overdueOnly) exp < todayStart else exp in todayStart..rangeEnd
            }
            .sortedBy { it.expiry!!.toDate().time }
            .toList()

        adapter.notifyDataSetChanged()
        empty.visibility = if (shownFoods.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun metaLine(item: FoodItem): Pair<String, Int?> {
        val ctx = requireContext()
        val exp = item.expiry!!.toDate().time
        val todayStart = startOfTodayMillis()
        val dayMs = 86_400_000L
        val days = ((exp - todayStart) / dayMs).toInt()
        val date = dateFmt.format(Date(exp))

        return when {
            days < 0 -> "Overdue • $date" to ContextCompat.getColor(ctx, android.R.color.holo_red_dark)
            days == 0 -> "Due today • $date" to ContextCompat.getColor(ctx, android.R.color.holo_orange_dark)
            else -> "Due in ${days}d • $date" to ContextCompat.getColor(ctx, android.R.color.holo_orange_dark)
        }
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
                }.show()
        }
    }
}
