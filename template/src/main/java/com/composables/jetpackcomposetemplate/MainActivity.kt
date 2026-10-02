package com.composables.jetpackcomposetemplate

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToInt

// --- МОДЕЛИ ДАННЫХ ---
data class Expense(val id: String, val amount: Int, val place: String, val category: String, val date: LocalDate)
data class PlannedExpense(val id: String, val monthStr: String, val name: String, val amount: Int)
data class WeekPeriod(val start: LocalDate, val end: LocalDate, val limit: Int, val spent: Int) {
    val remaining get() = limit - spent
}

// --- СОХРАНЕНИЕ ДАННЫХ ---
object Storage {
    private const val PREFS_NAME = "BudgetPrefs"

    fun saveExpenses(context: Context, expenses: List<Expense>) {
        val array = JSONArray()
        expenses.forEach { e ->
            val obj = JSONObject().apply {
                put("id", e.id); put("amount", e.amount); put("place", e.place)
                put("category", e.category); put("date", e.date.toString())
            }
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("expenses", array.toString()).apply()
    }

    fun loadExpenses(context: Context): List<Expense> {
        return try {
            val jsonStr = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("expenses", "[]") ?: "[]"
            val array = JSONArray(jsonStr)
            List(array.length()) { i ->
                val obj = array.getJSONObject(i)
                Expense(obj.getString("id"), obj.getInt("amount"), obj.getString("place"), obj.getString("category"), LocalDate.parse(obj.getString("date")))
            }
        } catch (e: Exception) { emptyList() }
    }

    fun savePlanned(context: Context, planned: List<PlannedExpense>) {
        val array = JSONArray()
        planned.forEach { p ->
            val obj = JSONObject().apply {
                put("id", p.id); put("monthStr", p.monthStr); put("name", p.name); put("amount", p.amount)
            }
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("planned", array.toString()).apply()
    }

    fun loadPlanned(context: Context): List<PlannedExpense> {
        return try {
            val jsonStr = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("planned", "[]") ?: "[]"
            val array = JSONArray(jsonStr)
            List(array.length()) { i ->
                val obj = array.getJSONObject(i)
                PlannedExpense(obj.getString("id"), obj.getString("monthStr"), obj.getString("name"), obj.getInt("amount"))
            }
        } catch (e: Exception) { emptyList() }
    }

    fun saveBudgets(context: Context, budgets: Map<YearMonth, Int>) {
        val obj = JSONObject()
        budgets.forEach { (ym, limit) -> obj.put(ym.toString(), limit) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("budgets", obj.toString()).apply()
    }

    fun loadBudgets(context: Context): Map<YearMonth, Int> {
        return try {
            val jsonStr = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("budgets", "{}") ?: "{}"
            val obj = JSONObject(jsonStr)
            val map = mutableMapOf<YearMonth, Int>()
            obj.keys().forEach { key -> map[YearMonth.parse(key)] = obj.getInt(key) }
            map
        } catch (e: Exception) { emptyMap() }
    }
}

// --- ГЛАВНЫЙ КЛАСС ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { BudgetApp() } }
    }
}

// --- ОСНОВНАЯ НАВИГАЦИЯ ---
@Composable
fun BudgetApp() {
    val context = LocalContext.current
    var expenses by remember { mutableStateOf(Storage.loadExpenses(context)) }
    var planned by remember { mutableStateOf(Storage.loadPlanned(context)) }
    var budgets by remember { mutableStateOf(Storage.loadBudgets(context)) }
    var currentTab by remember { mutableStateOf(0) }

    LaunchedEffect(expenses) { Storage.saveExpenses(context, expenses) }
    LaunchedEffect(planned) { Storage.savePlanned(context, planned) }
    LaunchedEffect(budgets) { Storage.saveBudgets(context, budgets) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(icon = { Icon(Icons.Default.Home, "") }, label = { Text("Главная") }, selected = currentTab == 0, onClick = { currentTab = 0 })
                NavigationBarItem(icon = { Icon(Icons.Default.Info, "") }, label = { Text("Аналитика") }, selected = currentTab == 1, onClick = { currentTab = 1 })
                NavigationBarItem(icon = { Icon(Icons.Default.List, "") }, label = { Text("История") }, selected = currentTab == 2, onClick = { currentTab = 2 })
                NavigationBarItem(icon = { Icon(Icons.Default.DateRange, "") }, label = { Text("Месяцы") }, selected = currentTab == 3, onClick = { currentTab = 3 })
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (currentTab) {
                0 -> HomeScreen(expenses, planned, budgets, 
                        onAddExpense = { expenses = expenses + it }, 
                        onAddPlanned = { planned = planned + it },
                        onDeletePlanned = { id -> planned = planned.filter { it.id != id } })
                1 -> AnalyticsScreen(expenses)
                2 -> HistoryScreen(expenses, onUpdate = { expenses = it })
                3 -> MonthsScreen(budgets, onUpdate = { budgets = it })
            }
        }
    }
}

// --- ВКЛАДКА 1: ГЛАВНАЯ ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(expenses: List<Expense>, planned: List<PlannedExpense>, budgets: Map<YearMonth, Int>, 
               onAddExpense: (Expense) -> Unit, onAddPlanned: (PlannedExpense) -> Unit, onDeletePlanned: (String) -> Unit) {
    
    var monthOffset by remember { mutableIntStateOf(0) }
    val displayMonth = YearMonth.now().plusMonths(monthOffset.toLong())
    val totalLimit = budgets[displayMonth]

    LazyColumn(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        item {
            // Свайпаемый блок лимитов
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = { /* Snap logic omitted for simplicity */ }
                    ) { change, dragAmount ->
                        change.consume()
                        if (dragAmount > 20) monthOffset -= 1
                        else if (dragAmount < -20) monthOffset += 1
                    }
                },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("<", fontWeight = FontWeight.Bold, modifier = Modifier.clickable { monthOffset -= 1 }.padding(8.dp))
                        Text("Распределение на $displayMonth", style = MaterialTheme.typography.titleMedium)
                        Text(">", fontWeight = FontWeight.Bold, modifier = Modifier.clickable { monthOffset += 1 }.padding(8.dp))
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    if (totalLimit == null) {
                        Text("Лимит не установлен. Зайдите в 'Месяцы'.", color = MaterialTheme.colorScheme.error)
                    } else {
                        val plannedForMonth = planned.filter { it.monthStr == displayMonth.toString() }.sumOf { it.amount }
                        val remainingForWeeks = totalLimit - plannedForMonth
                        
                        val daysInMonth = displayMonth.lengthOfMonth()
                        val dailyLimit = if (remainingForWeeks > 0) remainingForWeeks.toDouble() / daysInMonth else 0.0
                        
                        var currentStart = displayMonth.atDay(1)
                        while (currentStart.month == displayMonth.month) {
                            var currentEnd = currentStart
                            while (currentEnd.dayOfWeek.value != 7 && currentEnd.dayOfMonth < daysInMonth) {
                                currentEnd = currentEnd.plusDays(1)
                            }
                            val daysInWeek = currentEnd.dayOfMonth - currentStart.dayOfMonth + 1
                            val weekLimit = (dailyLimit * daysInWeek).roundToInt()
                            val spent = expenses.filter { it.date in currentStart..currentEnd }.sumOf { it.amount }
                            
                            val remaining = weekLimit - spent
                            val dateRange = "${currentStart.dayOfMonth}.${currentStart.monthValue} - ${currentEnd.dayOfMonth}.${currentEnd.monthValue}"
                            
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(dateRange)
                                Text("$remaining ₽", color = if (remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                            }
                            currentStart = currentEnd.plusDays(1)
                        }
                    }
                }
            }

            // Индикатор (3 точки)
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.Center) {
                repeat(3) { index ->
                    val isActive = (index == 1) // Центральная точка активна, так как мы всегда показываем 1 месяц
                    Box(modifier = Modifier.padding(4.dp).size(8.dp).clip(CircleShape).background(if (isActive) MaterialTheme.colorScheme.primary else Color.Gray))
                }
            }
        }

        item {
            // Дополнительные (запланированные) расходы
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Отложенные траты (вычитаются из лимита)", style = MaterialTheme.typography.titleMedium)
                    val monthPlanned = planned.filter { it.monthStr == displayMonth.toString() }
                    monthPlanned.forEach { p ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${p.name}: ${p.amount} ₽", fontWeight = FontWeight.Bold)
                            Text("Удалить", color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable { onDeletePlanned(p.id) }.padding(4.dp))
                        }
                    }

                    var pName by remember { mutableStateOf("") }
                    var pAmount by remember { mutableStateOf("") }
                    
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        OutlinedTextField(value = pName, onValueChange = { pName = it }, label = { Text("Название") }, modifier = Modifier.weight(1f).padding(end = 4.dp))
                        OutlinedTextField(value = pAmount, onValueChange = { pAmount = it }, label = { Text("Сумма") }, modifier = Modifier.weight(1f).padding(start = 4.dp))
                    }
                    Button(onClick = {
                        val amt = pAmount.toIntOrNull() ?: 0
                        if (amt > 0 && pName.isNotBlank()) {
                            onAddPlanned(PlannedExpense(UUID.randomUUID().toString(), displayMonth.toString(), pName, amt))
                            pName = ""; pAmount = ""
                        }
                    }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Добавить отложенную трату") }
                }
            }
        }

        item {
            // Стандартное добавление расхода
            var amountInput by remember { mutableStateOf("") }
            var placeInput by remember { mutableStateOf("") }
            var selectedCategory by remember { mutableStateOf("Продукты") }
            var selectedDate by remember { mutableStateOf(LocalDate.now()) }
            
            val context = LocalContext.current
            val datePickerDialog = DatePickerDialog(context, { _, y, m, d -> selectedDate = LocalDate.of(y, m + 1, d) }, selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)

            Text("Фактический расход", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = amountInput, onValueChange = { amountInput = it }, label = { Text("Сумма (₽)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = placeInput, onValueChange = { placeInput = it }, label = { Text("Место (комментарий)") }, modifier = Modifier.fillMaxWidth())
            
            Button(onClick = { datePickerDialog.show() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) { 
                Text("Дата: ${selectedDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}") 
            }

            var expanded by remember { mutableStateOf(false) }
            val categories = listOf("Продукты", "Транспорт / Авто", "Кафе", "Услуги / Красота", "Здоровье", "Развлечения", "Дом", "Иное")
            
            Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Button(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) { Text("Категория: $selectedCategory") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    categories.forEach { cat -> DropdownMenuItem(text = { Text(cat) }, onClick = { selectedCategory = cat; expanded = false }) }
                }
            }
            
            Button(
                onClick = {
                    val amount = amountInput.toIntOrNull() ?: 0
                    if (amount > 0) {
                        onAddExpense(Expense(UUID.randomUUID().toString(), amount, placeInput, selectedCategory, selectedDate))
                        amountInput = ""; placeInput = ""; selectedDate = LocalDate.now()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 32.dp)
            ) { Text("Записать расход") }
        }
    }
}

// --- ВКЛАДКА 2: АНАЛИТИКА ---
@Composable
fun AnalyticsScreen(expenses: List<Expense>) {
    var monthOffset by remember { mutableIntStateOf(0) }
    val displayMonth = YearMonth.now().plusMonths(monthOffset.toLong())

    val monthExpenses = expenses.filter { YearMonth.from(it.date) == displayMonth }
    val totalSpent = monthExpenses.sumOf { it.amount }
    
    // Группировка по категориям
    val grouped = monthExpenses.groupBy { it.category }
        .mapValues { it.value.sumOf { e -> e.amount } }
        .toList().sortedByDescending { it.second }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { monthOffset -= 1 }) { Text("<") }
            Text("Сводка за $displayMonth", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { monthOffset += 1 }) { Text(">") }
        }

        if (totalSpent == 0) {
            Text("В этом месяце трат пока нет.")
        } else {
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Всего потрачено", style = MaterialTheme.typography.bodyLarge)
                    Text("$totalSpent ₽", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
            }

            LazyColumn {
                items(grouped) { (cat, sum) ->
                    val progress = sum.toFloat() / totalSpent.toFloat()
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(cat, fontWeight = FontWeight.Bold)
                            Text("$sum ₽")
                        }
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth().height(8.dp).padding(top = 4.dp).clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
    }
}

// --- ВКЛАДКА 3: ИСТОРИЯ ---
@Composable
fun HistoryScreen(expenses: List<Expense>, onUpdate: (List<Expense>) -> Unit) {
    var editExpense by remember { mutableStateOf<Expense?>(null) }
    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("История трат", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))
        LazyColumn {
            items(expenses.sortedByDescending { it.date }) { exp ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { editExpense = exp }) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(exp.category, fontWeight = FontWeight.Bold)
                            Text("${exp.place} (${exp.date})", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("-${exp.amount} ₽", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
    if (editExpense != null) {
        AlertDialog(
            onDismissRequest = { editExpense = null },
            title = { Text("Удалить расход?") },
            text = { Text("Сумма вернется в лимит соответствующей недели.") },
            confirmButton = {
                Button(onClick = { onUpdate(expenses.filter { it.id != editExpense!!.id }); editExpense = null }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Удалить") }
            },
            dismissButton = { Button(onClick = { editExpense = null }) { Text("Отмена") } }
        )
    }
}

// --- ВКЛАДКА 4: НАСТРОЙКИ МЕСЯЦЕВ ---
@Composable
fun MonthsScreen(budgets: Map<YearMonth, Int>, onUpdate: (Map<YearMonth, Int>) -> Unit) {
    var newLimit by remember { mutableStateOf("") }
    var selectedMonth by remember { mutableStateOf(YearMonth.now().monthValue) }
    var selectedYear by remember { mutableStateOf(YearMonth.now().year) }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("Управление бюджетом", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Новый лимит", fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = selectedMonth.toString(), onValueChange = { selectedMonth = it.toIntOrNull() ?: 1 }, label = { Text("Месяц (1-12)") }, modifier = Modifier.weight(1f).padding(end = 4.dp))
                    OutlinedTextField(value = selectedYear.toString(), onValueChange = { selectedYear = it.toIntOrNull() ?: 2026 }, label = { Text("Год") }, modifier = Modifier.weight(1f).padding(start = 4.dp))
                }
                OutlinedTextField(value = newLimit, onValueChange = { newLimit = it }, label = { Text("Общая сумма (₽)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Button(onClick = {
                    val limitInt = newLimit.toIntOrNull()
                    if (limitInt != null && selectedMonth in 1..12) {
                        val ym = YearMonth.of(selectedYear, selectedMonth)
                        val newBudgets = budgets.toMutableMap()
                        newBudgets[ym] = limitInt
                        onUpdate(newBudgets)
                        newLimit = ""
                    }
                }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Сохранить лимит") }
            }
        }
        LazyColumn {
            items(budgets.toList().sortedByDescending { it.first }) { (ym, limit) ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(ym.toString(), fontWeight = FontWeight.Bold)
                        Text("$limit ₽")
                    }
                }
            }
        }
    }
}
