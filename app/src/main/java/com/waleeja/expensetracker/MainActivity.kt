package com.waleeja.expensetracker

import android.os.Bundle
import android.widget.*
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var etDescription: TextInputEditText
    private lateinit var etAmount: TextInputEditText
    private lateinit var btnAddIncome: MaterialButton
    private lateinit var btnAddExpense: MaterialButton
    private lateinit var tvBalance: TextView
    private lateinit var rvTransactions: RecyclerView
    private lateinit var spinnerCategory: Spinner
    private lateinit var tvIncomeAmount: TextView
    private lateinit var tvIncomeCount: TextView
    private lateinit var tvExpenseAmount: TextView
    private lateinit var tvExpenseCount: TextView

    private val transactions = mutableListOf<Transaction>()
    private lateinit var transactionAdapter: TransactionAdapter
    private var balance = 0.0

    private val PREFS_NAME = "expense_prefs"
    private val TRANSACTIONS_KEY = "transactions_list"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        initViews()
        setupCategorySpinner()
        loadTransactions()
        setupRecyclerView()
        setupListeners()
        updateBalance()
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val searchItem = menu?.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? androidx.appcompat.widget.SearchView

        searchView?.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = true

            override fun onQueryTextChange(newText: String?): Boolean {
                transactionAdapter.filterByQuery(newText.orEmpty())
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_filter -> {
                showFilterDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initViews() {
        etDescription = findViewById(R.id.etDescription)
        etAmount = findViewById(R.id.etAmount)
        btnAddIncome = findViewById(R.id.btnAddIncome)
        btnAddExpense = findViewById(R.id.btnAddExpense)
        tvBalance = findViewById(R.id.tvBalance)
        rvTransactions = findViewById(R.id.rvTransactions)
        spinnerCategory = findViewById(R.id.spinnerCategory)
        tvIncomeAmount = findViewById(R.id.tvIncomeAmount)
        tvIncomeCount = findViewById(R.id.tvIncomeCount)
        tvExpenseAmount = findViewById(R.id.tvExpenseAmount)
        tvExpenseCount = findViewById(R.id.tvExpenseCount)

    }

    private fun calculateTotalIncome(): Pair<Double, Int> {
        val incomeList = transactions.filter { it.type == TransactionType.INCOME }
        val total = incomeList.sumOf { it.amount }
        return Pair(total, incomeList.size)
    }

    private fun calculateTotalExpense(): Pair<Double, Int> {
        val expenseList = transactions.filter { it.type == TransactionType.EXPENSE }
        val total = expenseList.sumOf { it.amount }
        return Pair(total, expenseList.size)
    }

    private fun setupCategorySpinner() {
        val categories = resources.getStringArray(R.array.categories)
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = spinnerAdapter
    }

    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter(transactions) { transaction ->
            deleteTransaction(transaction)
        }
        rvTransactions.layoutManager = LinearLayoutManager(this)
        rvTransactions.adapter = transactionAdapter
    }

    private fun setupListeners() {
        btnAddIncome.setOnClickListener { addTransaction(TransactionType.INCOME) }
        btnAddExpense.setOnClickListener { addTransaction(TransactionType.EXPENSE) }
    }

    private fun addTransaction(type: TransactionType) {
        val description = etDescription.text.toString().trim()
        val amountStr = etAmount.text.toString().trim()
        val category = spinnerCategory.selectedItem?.toString() ?: ""

        if (description.isEmpty()) {
            Toast.makeText(this, "Please enter a description", Toast.LENGTH_SHORT).show()
            return
        }
        if (amountStr.isEmpty()) {
            Toast.makeText(this, "Please enter an amount", Toast.LENGTH_SHORT).show()
            return
        }
        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
            return
        }

        val transaction = Transaction(
            id = System.currentTimeMillis(),
            description = description,
            amount = amount,
            type = type,
            category = category,
            date = getCurrentDate()
        )

        transactions.add(0, transaction)
        transactionAdapter.notifyItemInserted(0)
        rvTransactions.smoothScrollToPosition(0)
        updateBalance()
        clearInputs()
        saveTransactions()

        val message = if (type == TransactionType.INCOME) "Income added" else "Expense added"
        Toast.makeText(this, "$message in $category", Toast.LENGTH_SHORT).show()
    }


    private fun deleteTransaction(transaction: Transaction) {
        val position = transactions.indexOf(transaction)
        if (position != -1) {
            transactions.removeAt(position)
            transactionAdapter.notifyItemRemoved(position)
            updateBalance()
            saveTransactions()
            Toast.makeText(this, "Transaction deleted", Toast.LENGTH_SHORT).show()
        }
    }


    private fun updateBalance() {
        balance = transactions.sumOf { if (it.type == TransactionType.INCOME) it.amount else -it.amount }
        tvBalance.text = String.format("$%.2f", balance)

        // Update Income card
        val (incomeTotal, incomeCount) = calculateTotalIncome()
        tvIncomeAmount.text = String.format("$%.2f", incomeTotal)
        tvIncomeCount.text = "$incomeCount transaction${if (incomeCount != 1) "s" else ""}"

        // Update Expense card
        val (expenseTotal, expenseCount) = calculateTotalExpense()
        tvExpenseAmount.text = String.format("$%.2f", expenseTotal)
        tvExpenseCount.text = "$expenseCount transaction${if (expenseCount != 1) "s" else ""}"
    }


    private fun clearInputs() {
        etDescription.text?.clear()
        etAmount.text?.clear()
        etDescription.requestFocus()
    }


    private fun getCurrentDate(): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return sdf.format(Date())
    }


    private fun saveTransactions() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val gson = Gson()
        val json = gson.toJson(transactions)
        editor.putString(TRANSACTIONS_KEY, json)
        editor.apply()
    }

    private fun loadTransactions() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val json = sharedPreferences.getString(TRANSACTIONS_KEY, null)
        if (json != null) {
            val type = object : TypeToken<MutableList<Transaction>>() {}.type
            val savedList: MutableList<Transaction> = gson.fromJson(json, type)
            transactions.addAll(savedList)
        }
    }

    override fun onPause() {
        super.onPause()
        saveTransactions()
    }


    private fun showFilterDialog() {
        val options = arrayOf("All Transactions", "Income Only", "Expense Only")
        var selectedOption = 0

        android.app.AlertDialog.Builder(this)
            .setTitle("Filter by Type")
            .setSingleChoiceItems(options, selectedOption) { _, which ->
                selectedOption = which
            }
            .setPositiveButton("Apply") { dialog, _ ->
                when (selectedOption) {
                    0 -> transactionAdapter.filterByType(null)
                    1 -> transactionAdapter.filterByType(TransactionType.INCOME)
                    2 -> transactionAdapter.filterByType(TransactionType.EXPENSE)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

data class Transaction(
    val id: Long,
    val description: String,
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val date: String
)

enum class TransactionType { INCOME, EXPENSE }

class TransactionAdapter(
    private val allTransactions: MutableList<Transaction>,
    private val onDeleteClick: (Transaction) -> Unit
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    private var filteredTransactions = allTransactions.toMutableList()

    class TransactionViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val tvDescription: TextView = view.findViewById(R.id.tvTransactionDesc)
        val tvAmount: TextView = view.findViewById(R.id.tvTransactionAmount)
        val tvDate: TextView = view.findViewById(R.id.tvTransactionDate)
        val tvCategory: TextView = view.findViewById(R.id.tvTransactionCategory)
        val btnDelete: MaterialButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int):
            TransactionViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = filteredTransactions[position]
        holder.tvDescription.text = transaction.description
        holder.tvDate.text = transaction.date
        holder.tvCategory.text = transaction.category

        val amountText = String.format("$%.2f", transaction.amount)
        holder.tvAmount.text =
            if (transaction.type == TransactionType.INCOME) "+$amountText" else "-$amountText"

        val color = if (transaction.type == TransactionType.INCOME)
            android.graphics.Color.parseColor("#4CAF50")
        else
            android.graphics.Color.parseColor("#F44336")

        holder.tvAmount.setTextColor(color)
        holder.btnDelete.setOnClickListener { onDeleteClick(transaction) }
    }

    override fun getItemCount() = filteredTransactions.size

    fun filterByQuery(query: String) {
        filteredTransactions = if (query.isEmpty()) {
            allTransactions.toMutableList()
        } else {
            allTransactions.filter { it.description.contains(query, ignoreCase = true) }
                .toMutableList()
        }
        notifyDataSetChanged()
    }

    fun filterByType(type: TransactionType?) {
        filteredTransactions = if (type == null) {
            allTransactions.toMutableList()
        } else {
            allTransactions.filter { it.type == type }.toMutableList()
        }
        notifyDataSetChanged()
    }
}
