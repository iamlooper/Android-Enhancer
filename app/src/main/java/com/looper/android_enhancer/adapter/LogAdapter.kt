package com.looper.android_enhancer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import androidx.recyclerview.widget.RecyclerView

import com.looper.android_enhancer.R

class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    // List to store log lines.
    private var logLines: List<String> = ArrayList()

    // Function to set data for the adapter and notify the changes.
    fun setData(lines: List<String>) {
        logLines = lines
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        // Get log line from the list and bind it to the view holder.
        holder.bind(logLines[position])
    }
    
    override fun getItemCount(): Int {
        return logLines.size
    }

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // TextView to display the log line.
        private val logTextView: TextView = itemView.findViewById(R.id.log_line_text_view)

        // Bind the log line to the TextView.
        fun bind(logLine: String) {
            logTextView.text = logLine
        }
    }
}
