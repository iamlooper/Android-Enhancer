package com.looper.android_enhancer.fragment

import java.io.File

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.view.Menu
import android.view.MenuItem
import android.view.MenuInflater
import android.os.Handler
import android.os.Looper

import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.looper.android.support.util.FileUtils
import com.looper.android.support.util.IntentUtils
import com.looper.android.support.util.UIUtils

import com.looper.android_enhancer.adapter.LogAdapter
import com.looper.android_enhancer.R

class LogFragment : Fragment() {

    // Initialize UI components and variables.
    private lateinit var logRecyclerView: RecyclerView
    private lateinit var emptyLogTextView: TextView
    private lateinit var logFile: File
    private lateinit var logAdapter: LogAdapter
    private val handler = Handler(Looper.getMainLooper())
    private val updateLogRunnable: Runnable = object : Runnable {
        override fun run() {
            // Update log content every 1 second.
            updateLogContent()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment.
        val view = inflater.inflate(R.layout.fragment_log, container, false)
        
        // Enable options menu in this fragment.
        setHasOptionsMenu(true)        

        // Find RecyclerView and TextView in the layout.
        logRecyclerView = view.findViewById(R.id.log_recycler_view)
        emptyLogTextView = view.findViewById(R.id.empty_log_text_view)
              
        // Get the log file from the app's files directory.
        logFile = File(requireContext().filesDir, "log.txt")
                       
        // Create an adapter for the RecyclerView and set its layout manager.
        logAdapter = LogAdapter()
        logRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        logRecyclerView.adapter = logAdapter
            
        // Add a divider between RecyclerView items.
        logRecyclerView.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        
        // Start updating log content.
        handler.postDelayed(updateLogRunnable, 0)

        return view
    }
   
    override fun onDestroyView() {
        super.onDestroyView()
        // Remove the runnable callbacks.
        handler.removeCallbacks(updateLogRunnable)
    }    
        
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.fragment_log_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {    
        return when (item.itemId) {
            R.id.action_share_log -> {
                // Share log file via Intent if it exists, otherwise show a snackbar message.
                if (logFile.exists()) {
                    IntentUtils.shareFile(requireContext(), logFile, "text/plain")
                } else {
                    UIUtils.showSnackbarWithAction(requireView(), requireContext().getString(R.string.text_empty_log), "Dismiss", UIUtils.SNACKBAR_LENGTH_SHORT) { /* No operation */ }
                }
                true
            }
            R.id.action_delete_log -> {
                // Delete log file if it exists, otherwise show a snackbar message.
                if (logFile.exists()) {
                    FileUtils.delete(logFile)
                    // Show a snackbar message when log file is deleted.
                    UIUtils.showSnackbarWithAction(requireView(), requireContext().getString(R.string.snackbar_log_deleted), "Dismiss", UIUtils.SNACKBAR_LENGTH_SHORT) { /* No operation */ }
                } else {
                    UIUtils.showSnackbarWithAction(requireView(), requireContext().getString(R.string.text_empty_log), "Dismiss", UIUtils.SNACKBAR_LENGTH_SHORT) { /* No operation */ }
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    // Update the log content based on the log file's existence.
    private fun updateLogContent() {
        if (logFile.exists()) {
            // Read log file content and update RecyclerView.
            val logLines: List<String> = FileUtils.read(logFile)         
            logAdapter.setData(logLines)
            logRecyclerView.visibility = View.VISIBLE
            emptyLogTextView.visibility = View.GONE
        } else {
            // Hide RecyclerView and show empty log message.
            logRecyclerView.visibility = View.GONE
            emptyLogTextView.visibility = View.VISIBLE
            logAdapter.setData(emptyList())
        }
    }    
}
