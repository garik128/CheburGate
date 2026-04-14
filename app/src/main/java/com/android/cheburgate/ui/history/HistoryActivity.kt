package com.android.cheburgate.ui.history

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.cheburgate.R
import com.android.cheburgate.data.db.AppDatabase
import com.android.cheburgate.data.model.HistoryItem
import com.android.cheburgate.databinding.ActivityHistoryBinding
import com.android.cheburgate.ui.browser.BrowserActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val adapter = HistoryAdapter { item ->
            startActivity(Intent(this, BrowserActivity::class.java).apply {
                putExtra(BrowserActivity.EXTRA_URL, item.url)
                putExtra(BrowserActivity.EXTRA_SERVICE_NAME, item.title ?: item.host)
            })
        }

        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter

        lifecycleScope.launch {
            AppDatabase.getInstance(this@HistoryActivity)
                .historyDao()
                .getAllFlow()
                .collectLatest { items -> adapter.submitList(items) }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}

class HistoryAdapter(
    private val onClick: (HistoryItem) -> Unit
) : ListAdapter<HistoryItem, HistoryAdapter.VH>(DIFF) {

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvUrl: TextView = view.findViewById(R.id.tvUrl)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.tvTitle.text = item.title ?: item.host
        holder.tvUrl.text = item.url
        holder.tvDate.text = dateFormat.format(Date(item.visitedAt))
        holder.itemView.setOnClickListener { onClick(item) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<HistoryItem>() {
            override fun areItemsTheSame(a: HistoryItem, b: HistoryItem) = a.id == b.id
            override fun areContentsTheSame(a: HistoryItem, b: HistoryItem) = a == b
        }
    }
}
