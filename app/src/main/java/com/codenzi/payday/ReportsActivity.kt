// Konum: app/src/main/java/com/codenzi/payday/ReportsActivity.kt
// Hata Düzeltilmiş Nihai Sürüm

package com.codenzi.payday

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat // <-- HATA İÇİN EKLENEN SATIR
import com.codenzi.payday.databinding.ActivityReportsBinding
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import java.util.Calendar
import java.util.Date

class ReportsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportsBinding
    private val viewModel: PaydayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupCharts()
        setupFilters()
        setupObservers()

        // Başlangıç verilerini yükle (son 30 gün)
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
        // PieChart Kurulumu
        binding.pieChart.apply {
            setUsePercentValues(true)
            description.isEnabled = false
            isDrawHoleEnabled = true
            setHoleColor(Color.TRANSPARENT)
            setTransparentCircleColor(Color.WHITE)
            setTransparentCircleAlpha(110)
            holeRadius = 58f
            transparentCircleRadius = 61f
            setDrawCenterText(true)
            centerText = getString(R.string.spending_by_category)
            animateY(1400, Easing.EaseInOutQuad)
            legend.isWordWrapEnabled = true
        }

        // BarChart Kurulumu
        binding.barChart.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            animateY(1500)
            legend.isEnabled = false
            axisLeft.axisMinimum = 0f
            axisRight.isEnabled = false
            // Eksen ayarları
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
        }

        // LineChart Kurulumu
        binding.lineChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            animateX(1500)
            axisLeft.axisMinimum = 0f
            axisRight.isEnabled = false
            // Eksen ayarları
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
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
            binding.pieChart.visibility = if (hasPieData) View.VISIBLE else View.GONE
            if (hasPieData) {
                setDataToPieChart(state.categorySpendingData)
            }
        }

        viewModel.dailySpendingData.observe(this) { (entries, labels) ->
            binding.barChart.visibility = if (entries.isNotEmpty()) View.VISIBLE else View.GONE
            if (entries.isNotEmpty()) {
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

        // Genel boş veri durumunu kontrol et
        viewModel.uiState.observe(this) { state ->
            val pieIsEmpty = state.categorySpendingData.all { it.y == 0f }
            val barIsEmpty = viewModel.dailySpendingData.value?.first.isNullOrEmpty()
            val lineIsEmpty = viewModel.monthlyCategorySpendingData.value?.first.isNullOrEmpty()

            if (pieIsEmpty && barIsEmpty && lineIsEmpty) {
                binding.emptyChartTextView.visibility = View.VISIBLE
            } else {
                binding.emptyChartTextView.visibility = View.GONE
            }
        }
    }

    private fun setDataToPieChart(entries: List<PieEntry>) {
        val dataSet = PieDataSet(entries.filter{ it.y > 0 }, "") // Sadece değeri 0'dan büyük olanları al
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 5f
        dataSet.colors = resources.getIntArray(R.array.pie_chart_colors).toList()

        val data = PieData(dataSet)
        data.setValueFormatter(PercentFormatter(binding.pieChart))
        data.setValueTextSize(12f)
        data.setValueTextColor(Color.BLACK)

        binding.pieChart.data = data
        binding.pieChart.invalidate()
    }

    private fun setDataToBarChart(entries: List<BarEntry>, labels: List<String>) {
        val dataSet = BarDataSet(entries, "Günlük Harcamalar")
        dataSet.color = ContextCompat.getColor(this, R.color.primary)
        dataSet.valueTextColor = Color.DKGRAY

        val dataSets = ArrayList<IBarDataSet>()
        dataSets.add(dataSet)

        val data = BarData(dataSets)
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
        dataSet.setDrawCircleHole(false)
        dataSet.valueTextSize = 10f
        dataSet.setDrawFilled(true)
        dataSet.fillDrawable = ContextCompat.getDrawable(this, R.drawable.chart_gradient)
        dataSet.fillAlpha = 150

        val dataSets = ArrayList<ILineDataSet>()
        dataSets.add(dataSet)

        val data = LineData(dataSets)
        binding.lineChart.data = data

        val xAxis = binding.lineChart.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.setLabelCount(labels.size, false)
        xAxis.granularity = 1f
        xAxis.isGranularityEnabled = true

        binding.lineChart.invalidate()
    }
}