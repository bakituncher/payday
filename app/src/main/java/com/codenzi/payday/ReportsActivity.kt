package com.codenzi.payday

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat // <-- EKLENDİ
import com.codenzi.payday.databinding.ActivityReportsBinding
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.google.android.material.color.MaterialColors
import java.util.Calendar
import java.util.Date

class ReportsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportsBinding
    private val viewModel: PaydayViewModel by viewModels()
    private var chartTextColor: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Kenardan kenara görünüm için DÜZELTME
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chartTextColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
        viewModel.triggerReportsViewedAchievement()

        setupToolbar()
        setupCharts()
        setupFilters()
        setupObservers()

        binding.reportsEmptyState.root.findViewById<Button>(R.id.add_expense_button).setOnClickListener {
            finish()
        }

        val calendar = Calendar.getInstance()
        val endDate = calendar.time
        calendar.add(Calendar.DAY_OF_YEAR, -30)
        val startDate = calendar.time
        viewModel.loadDailySpending(startDate, endDate)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupCharts() {
        binding.pieChart.apply {
            setUsePercentValues(true)
            description.isEnabled = false
            isDrawHoleEnabled = true
            setHoleColor(Color.TRANSPARENT)
            setCenterTextColor(chartTextColor)
            legend.textColor = chartTextColor
            setEntryLabelColor(chartTextColor)
            centerText = getString(R.string.spending_by_category)
            animateY(1400, Easing.EaseInOutQuad)
        }

        binding.barChart.apply {
            description.isEnabled = false
            xAxis.textColor = chartTextColor
            axisLeft.textColor = chartTextColor
            axisRight.isEnabled = false
            legend.isEnabled = false
            axisLeft.axisMinimum = 0f
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            animateY(1500)
        }

        binding.lineChart.apply {
            description.isEnabled = false
            xAxis.textColor = chartTextColor
            axisLeft.textColor = chartTextColor
            axisRight.isEnabled = false
            legend.textColor = chartTextColor
            axisLeft.axisMinimum = 0f
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            animateX(1500)
        }
    }

    private fun setupFilters() {
        val categories = ExpenseCategory.entries.map { it.categoryName }.toTypedArray()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.categorySpinner.adapter = adapter

        binding.categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedCategory = ExpenseCategory.entries[position]
                viewModel.loadMonthlySpendingForCategory(selectedCategory.ordinal)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupObservers() {
        viewModel.uiState.observe(this) { state ->
            val hasPieData = state.categorySpendingData.any { it.y > 0 }
            updateVisibility(hasPieData || binding.barChart.data?.entryCount ?: 0 > 0)
            if (hasPieData) {
                setDataToPieChart(state.categorySpendingData)
            }
        }

        viewModel.dailySpendingData.observe(this) { (entries, labels) ->
            val hasBarData = entries.isNotEmpty()
            updateVisibility(hasBarData || binding.pieChart.data?.entryCount ?: 0 > 0)
            if (hasBarData) {
                setDataToBarChart(entries, labels)
            }
        }

        viewModel.monthlyCategorySpendingData.observe(this) { (entries, labels) ->
            val hasLineData = entries.isNotEmpty()
            binding.lineChart.visibility = if (hasLineData) View.VISIBLE else View.GONE
            binding.categorySpinner.visibility = if (hasLineData) View.VISIBLE else View.GONE
            if (hasLineData) {
                setDataToLineChart(entries, labels)
            }
        }
    }

    private fun updateVisibility(hasData: Boolean) {
        if (hasData) {
            binding.reportsScrollView.visibility = View.VISIBLE
            binding.reportsEmptyState.root.visibility = View.GONE
        } else {
            binding.reportsScrollView.visibility = View.GONE
            binding.reportsEmptyState.root.visibility = View.VISIBLE
        }
    }

    private fun setDataToPieChart(entries: List<PieEntry>) {
        val dataSet = PieDataSet(entries.filter { it.y > 0 }, "")
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 5f
        dataSet.colors = resources.getIntArray(R.array.pie_chart_colors).toList()

        val data = PieData(dataSet)
        data.setValueFormatter(PercentFormatter(binding.pieChart))
        data.setValueTextSize(12f)
        data.setValueTextColor(Color.WHITE)

        binding.pieChart.data = data
        binding.pieChart.invalidate()
    }

    private fun setDataToBarChart(entries: List<BarEntry>, labels: List<String>) {
        val dataSet = BarDataSet(entries, "Günlük Harcamalar")
        dataSet.color = ContextCompat.getColor(this, R.color.primary)
        dataSet.valueTextColor = chartTextColor

        val data = BarData(listOf<IBarDataSet>(dataSet))
        data.setValueTextSize(10f)
        data.barWidth = 0.9f

        binding.barChart.data = data

        val xAxis = binding.barChart.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.setLabelCount(labels.size, false)
        xAxis.granularity = 1f
        xAxis.isGranularityEnabled = true

        binding.barChart.invalidate()
    }

    private fun setDataToLineChart(entries: List<Entry>, labels: List<String>) {
        val dataSet = LineDataSet(entries, "Aylık Harcama")
        dataSet.color = ContextCompat.getColor(this, R.color.secondary)
        dataSet.setCircleColor(ContextCompat.getColor(this, R.color.secondary))
        dataSet.lineWidth = 2f
        dataSet.circleRadius = 4f
        dataSet.valueTextColor = chartTextColor
        dataSet.setDrawCircleHole(false)
        dataSet.valueTextSize = 10f
        dataSet.setDrawFilled(true)
        dataSet.fillDrawable = ContextCompat.getDrawable(this, R.drawable.chart_gradient)
        dataSet.fillAlpha = 150

        val data = LineData(listOf<ILineDataSet>(dataSet))
        binding.lineChart.data = data

        val xAxis = binding.lineChart.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.setLabelCount(labels.size, false)
        xAxis.granularity = 1f
        xAxis.isGranularityEnabled = true

        binding.lineChart.invalidate()
    }
}