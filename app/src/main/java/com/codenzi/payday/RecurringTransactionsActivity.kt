package com.codenzi.payday

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat // <-- EKLENDİ
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.codenzi.payday.databinding.ActivityRecurringTransactionsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RecurringTransactionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecurringTransactionsBinding
    private val viewModel: PaydayViewModel by viewModels()
    private lateinit var recurringTransactionAdapter: RecurringTransactionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Kenardan kenara görünüm için DÜZELTME
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityRecurringTransactionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        observeRecurringTransactions()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        recurringTransactionAdapter = RecurringTransactionAdapter(
            onEditClicked = { transaction ->
                TransactionDialogFragment.newInstance(transaction.id)
                    .show(supportFragmentManager, TransactionDialogFragment.TAG)
            },
            onDeleteClicked = { transaction ->
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.delete_recurring_transaction_title)
                    .setMessage(getString(R.string.delete_recurring_transaction_message, transaction.name))
                    .setNegativeButton(getString(R.string.cancel), null)
                    .setPositiveButton(getString(R.string.delete)) { _, _ ->
                        viewModel.deleteRecurringTemplate(transaction)
                    }
                    .show()
            }
        )
        binding.recurringTransactionsRecyclerView.apply {
            adapter = recurringTransactionAdapter
            layoutManager = LinearLayoutManager(this@RecurringTransactionsActivity)
        }
    }

    private fun observeRecurringTransactions() {
        lifecycleScope.launch {
            val repository = PaydayRepository(this@RecurringTransactionsActivity)
            repository.getRecurringTransactionTemplates().collectLatest { templates ->
                if (templates.isEmpty()) {
                    binding.recurringTransactionsRecyclerView.visibility = View.GONE
                    binding.emptyStateView.visibility = View.VISIBLE
                } else {
                    binding.recurringTransactionsRecyclerView.visibility = View.VISIBLE
                    binding.emptyStateView.visibility = View.GONE
                    recurringTransactionAdapter.submitList(templates)
                }
            }
        }
    }
}