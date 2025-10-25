package com.example.foodguard3

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class HomeFragment : Fragment() {

    // No more `by lazy`
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var tvGreeting: TextView
    private lateinit var tvSummary: TextView
    private lateinit var tvSeeAll: TextView
    private lateinit var rvSoon: RecyclerView
    private lateinit var tvEmpty: TextView

    private lateinit var cardSuggest: View
    private lateinit var tvSuggestTitle: TextView
    private lateinit var tvSug1: TextView
    private lateinit var tvSug2: TextView
    private lateinit var tvSug3: TextView

    private val adapter = SoonAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.fragment_home, container, false)

        // init singletons plainly
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        tvGreeting = v.findViewById(R.id.tvGreeting)
        tvSummary  = v.findViewById(R.id.tvSummary)
        tvSeeAll   = v.findViewById(R.id.tvSeeAll)
        rvSoon     = v.findViewById(R.id.rvSoon)
        tvEmpty    = v.findViewById(R.id.tvEmpty)

        cardSuggest     = v.findViewById(R.id.cardSuggest)
        tvSuggestTitle  = v.findViewById(R.id.tvSuggestTitle)
        tvSug1          = v.findViewById(R.id.tvSug1)
        tvSug2          = v.findViewById(R.id.tvSug2)
        tvSug3          = v.findViewById(R.id.tvSug3)

        rvSoon.layoutManager = LinearLayoutManager(requireContext())
        rvSoon.adapter = adapter

        tvSeeAll.setOnClickListener {
            (requireActivity().findViewById(R.id.bottomNav) as? BottomNavigationView)
                ?.selectedItemId = R.id.nav_expiring
        }

        loadGreeting()
        listenTop3IncludingToday()

        return v
    }

    private fun loadGreeting() {
        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { snap ->
                val first = (snap?.getString("firstName") ?: user.email ?: "there")
                tvGreeting.text = "Hi, $first!"
            }
            .addOnFailureListener { tvGreeting.text = "Hi!" }
    }

    /** Start-of-today (local) in ms */
    private fun startOfTodayMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Real-time: Top 3 with expiry in [today .. today+7d], client-filters status to avoid composite index */
    private fun listenTop3IncludingToday() {
        val user = auth.currentUser ?: return

        val startMs = startOfTodayMillis()
        val endMs = startMs + TimeUnit.DAYS.toMillis(7)
        val startTs = Timestamp(Date(startMs))
        val endTs = Timestamp(Date(endMs))

        db.collection("users").document(user.uid)
            .collection("foods")
            .whereGreaterThanOrEqualTo("expiry", startTs)
            .whereLessThanOrEqualTo("expiry", endTs)
            .orderBy("expiry", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    showEmpty("Couldn’t load expiring items.")
                    return@addSnapshotListener
                }

                val all = snap?.documents?.mapNotNull {
                    it.toObject(FoodItem::class.java)?.copy(id = it.id)
                }.orEmpty()

                val active = all.filter { it.status == "active" }
                val top3 = active.take(3)

                adapter.submit(top3)
                val empty = top3.isEmpty()
                tvEmpty.isVisible = empty
                rvSoon.isGone = empty
                tvSummary.text = "Top ${top3.size} items expiring within 7 days."

                if (top3.isNotEmpty()) {
                    // Fetch online recipe ideas for the #1 item
                    fetchOnlineSuggestionsFor(top3.first()) { ideas ->
                        if (ideas.isEmpty()) {
                            cardSuggest.visibility = View.GONE
                        } else {
                            tvSuggestTitle.text =
                                "${top3.first().name} is near expiry — here are some ideas:"
                            cardSuggest.visibility = View.VISIBLE
                            val lines = listOf(tvSug1, tvSug2, tvSug3)
                            lines.forEachIndexed { i, tv ->
                                if (i < ideas.size) {
                                    tv.text = "• ${ideas[i]}"
                                    tv.visibility = View.VISIBLE
                                } else {
                                    tv.visibility = View.GONE
                                }
                            }
                        }
                    }
                } else {
                    cardSuggest.visibility = View.GONE
                }
            }
    }

    private fun showEmpty(msg: String) {
        tvEmpty.isVisible = true
        rvSoon.isGone = true
        tvSummary.text = msg
        cardSuggest.visibility = View.GONE
    }

    /**
     * Find a reasonable ingredient keyword from the item to query TheMealDB.
     * e.g., "Beef loaf" -> "beef", "Chicken breast" -> "chicken"
     */
    private fun primaryIngredientKeyword(item: FoodItem): String {
        val n = item.name.lowercase(Locale.getDefault())
        val c = item.category.lowercase(Locale.getDefault())

        val known = listOf(
            "beef","pork","chicken","fish","tuna","salmon","sardine","egg",
            "noodle","pasta","bread","rice","potato","tomato","carrot","onion"
        )
        for (k in known) if (n.contains(k)) return k

        // fallback: derive from category
        return when {
            c.contains("meat") -> "beef"
            c.contains("chicken") -> "chicken"
            c.contains("fish") || c.contains("seafood") -> "fish"
            c.contains("noodle") || c.contains("pasta") -> "pasta"
            c.contains("fruit") -> "apple"
            c.contains("vegetable") -> "vegetable"
            c.contains("canned") -> "chicken"
            else -> "rice"
        }
    }

    /**
     * Fetch 3 random recipe titles from TheMealDB for the top item’s ingredient.
     * Uses plain HttpURLConnection (no extra deps). Falls back to empty on failure.
     *
     * API: https://www.themealdb.com/api/json/v1/1/filter.php?i=<ingredient>
     */
    private fun fetchOnlineSuggestionsFor(top: FoodItem, onDone: (List<String>) -> Unit) {
        Thread {
            val ideas = try {
                val ingredient = primaryIngredientKeyword(top)
                val encoded = URLEncoder.encode(ingredient, "UTF-8")
                val url = URL("https://www.themealdb.com/api/json/v1/1/filter.php?i=$encoded")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                conn.inputStream.use { ins ->
                    val text = ins.bufferedReader().readText()
                    val root = JSONObject(text)
                    val meals = root.optJSONArray("meals") ?: return@Thread postDone(emptyList(), onDone)
                    // collect meal names
                    val names = mutableListOf<String>()
                    for (i in 0 until meals.length()) {
                        val obj = meals.getJSONObject(i)
                        val name = obj.optString("strMeal")
                        if (name.isNotBlank()) names += name
                    }
                    // random 3 unique
                    names.shuffled(Random(System.currentTimeMillis())).take(3)
                }
            } catch (_: Throwable) {
                emptyList<String>()
            }
            postDone(ideas, onDone)
        }.start()
    }

    private fun postDone(ideas: List<String>, onDone: (List<String>) -> Unit) {
        if (!isAdded) return
        requireActivity().runOnUiThread { onDone(ideas) }
    }

    // --- Adapter for the 3-item list ---
    private inner class SoonAdapter : RecyclerView.Adapter<SoonVH>() {
        private val data = mutableListOf<FoodItem>()
        fun submit(newData: List<FoodItem>) { data.clear(); data.addAll(newData); notifyDataSetChanged() }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SoonVH {
            val v = layoutInflater.inflate(R.layout.item_food_home, parent, false)
            return SoonVH(v)
        }
        override fun onBindViewHolder(holder: SoonVH, position: Int) = holder.bind(data[position])
        override fun getItemCount(): Int = data.size
    }

    private inner class SoonVH(v: View) : RecyclerView.ViewHolder(v) {
        private val tvName = v.findViewById<TextView>(R.id.tvName)
        private val tvCategory = v.findViewById<TextView>(R.id.tvCategory)
        private val tvDate = v.findViewById<TextView>(R.id.tvDate)
        private val tvDaysLeft = v.findViewById<TextView>(R.id.tvDaysLeft)
        private val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

        fun bind(item: FoodItem) {
            tvName.text = item.name
            tvCategory.text = item.category

            val ms = item.expiry?.toDate()?.time ?: 0L
            tvDate.text = fmt.format(Date(ms))

            val daysLeft = daysUntil(ms)
            tvDaysLeft.text = "${daysLeft}d"
            val color = when {
                daysLeft <= 0 -> 0xFFD32F2F.toInt() // red
                daysLeft <= 2 -> 0xFFFFA000.toInt() // amber
                else -> 0xFF616161.toInt()
            }
            tvDaysLeft.setTextColor(color)
        }

        private fun daysUntil(targetMs: Long): Int {
            val startToday = startOfTodayMillis()
            val diff = targetMs - startToday
            return (diff / TimeUnit.DAYS.toMillis(1)).toInt()
        }
    }
}
