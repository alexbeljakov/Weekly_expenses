package com.composables.jetpackcomposetemplate

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
data class WeekPeriod(val start: LocalDate, val end: LocalDate, val limit: Int, val spent: Int) {
    val remaining get() = limit - spent
}

// --- СОХРАНЕНИЕ ДАННЫХ (SharedPreferences) ---
object Storage {
    private const val PREFS_NAME = "BudgetPrefs"

fun saveExpenses(context: Context, expenses: List<Expense>) {
    val array = JSONArray()
    expenses.forEach { e ->
        val obj = JSONObject()
        obj.put("id", e.id)
        obj.put("amount", e.amount)
        obj.put("place", e.place)
        obj.put("category", e.category)
        obj.put("date", e.date.toString())
        array.put(obj)
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("expenses", array.toString()).apply()
}

fun loadExpenses(context: Context): List<Expense> {
    val jsonStr = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("expenses", "[]") ?: "[]"
    val array = JSONArray(jsonStr)
    val list = mutableListOf<Expense>()
    for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        list.add(Expense(
            id = obj.getString("id"),
            amount = obj.getInt("amount"),
            place = obj.getString("place"),
            category = obj.getString("category"),
            date = LocalDate.parse(obj.getString("date"))
        ))
    }
    return list
}

fun saveBudgets(context: Context, budgets: Map<YearMonth, Int>) {
    val obj = JSONObject()
    budgets.forEach { (ym, limit) -> obj.put(ym.toString(), limit) }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("budgets", obj.toString()).apply()
}

fun loadBudgets(context: Context): Map<YearMonth, Int> {
    val jsonStr = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("budgets", "{}") ?: "{}"
    val obj = JSONObject(jsonStr)
    val map = mutableMapOf<YearMonth, Int>()
    obj.keys().forEach { key -> map[YearMonth.parse(key)] = obj.getInt(key) }
    return map
}
}

// --- ГЛАВНЫЙ КЛАСС ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                BudgetApp()
            }
        }
    }
}

// --- ИНТЕРФЕЙС И ЛОГИКА ---
@Composable
fun BudgetApp() {
    val context = LocalContext.current
    var expenses by remember { mutableStateOf(Storage.loadExpenses(context)) }
    var budgets by remember { mutableStateOf(Storage.loadBudgets(context)) }
    var currentTab by remember { mutableStateOf(0) }

// Сохраняем данные при каждом изменении списков
LaunchedEffect(expenses) { Storage.saveExpenses(context, expenses) }
LaunchedEffect(budgets) { Storage.saveBudgets(context, budgets) }

Scaffold(
    bottomBar = {
        NavigationBar {
            NavigationBarItem(
                icon = { Icon(Icons.Default.Home, "Главная") },
                label = { Text("Главная") },
                selected = currentTab == 0,
                onClick = { currentTab = 0 }
            )
            NavigationBarItem(
                icon = { Icon(Icons.Default.DateRange, "История") },
                label = { Text("История") },
                selected = currentTab == 1,
                onClick = { currentTab = 1 }
            )
            NavigationBarItem(
                icon = { Icon(Icons.Default.Settings, "Месяцы") },
                label = { Text("Месяцы") },
                selected = currentTab == 2,
                onClick = { currentTab = 2 }
            )
        }
    }
) { padding ->
    Box(modifier = Modifier.padding(padding).fillMaxSize()) {
        when (currentTab) {
            0 -> HomeScreen(expenses, budgets, onAddExpense = { expenses = expenses + it })
            1 -> HistoryScreen(expenses, onUpdate = { expenses = it })
            2 -> MonthsScreen(budgets, onUpdate = { budgets = it })
        }
    }
}
}

// --- ВКЛАДКА 1: ГЛАВНАЯ (Траты и Лимиты) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(expenses: List<Expense>, budgets: Map<YearMonth, Int>, onAddExpense: (Expense) -> Unit) {
    val currentMonth = YearMonth.now()
    val totalLimit = budgets[currentMonth]

Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
    if (totalLimit == null) {
        Text("Лимит на этот месяц (${currentMonth}) не установлен. Перейдите во вкладку 'Месяцы'.", color = MaterialTheme.colorScheme.error)
    } else {
        // Математика недель
        val daysInMonth = currentMonth.lengthOfMonth()
        val dailyLimit = totalLimit.toDouble() / daysInMonth
        val weeks = mutableListOf<WeekPeriod>()
        
        var currentStart = currentMonth.atDay(1)
        while (currentStart.month == currentMonth.month) {
            var currentEnd = currentStart
            while (currentEnd.dayOfWeek.value != 7 && currentEnd.dayOfMonth < daysInMonth) {
                currentEnd = currentEnd.plusDays(1)
            }
            val daysInWeek = currentEnd.dayOfMonth - currentStart.dayOfMonth + 1
            val weekLimit = (dailyLimit * daysInWeek).roundToInt()
            
            // Считаем потраченное за эту неделю
            val spent = expenses.filter { it.date in currentStart..currentEnd }.sumOf { it.amount }
            weeks.add(WeekPeriod(currentStart, currentEnd, weekLimit, spent))
            currentStart = currentEnd.plusDays(1)
        }

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Распределение на ${currentMonth}", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                weeks.forEach { week ->
                    val dateRange = "${week.start.dayOfMonth}.${week.start.monthValue} - ${week.end.dayOfMonth}.${week.end.monthValue}"
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(dateRange)
                        Text("${week.remaining} ₽", color = if (week.remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Форма добавления
        var amountInput by remember { mutableStateOf("") }
        var placeInput by remember { mutableStateOf("") }
        var selectedCategory by remember { mutableStateOf("Продукты") }
        var selectedDate by remember { mutableStateOf(LocalDate.now()) }
        
        val context = LocalContext.current
        val datePickerDialog = DatePickerDialog(context, { _, year, month, day -> selectedDate = LocalDate.of(year, month + 1, day) }, selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)

        Text("Добавить расход", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = amountInput, onValueChange = { amountInput = it }, label = { Text("Сумма (₽)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = placeInput, onValueChange = { placeInput = it }, label = { Text("Место (комментарий)") }, modifier = Modifier.fillMaxWidth())
        
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = { datePickerDialog.show() }) { Text("Дата: ${selectedDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}") }
        }

        // Выбор категории
        var expanded by remember { mutableStateOf(false) }
        val categories = listOf("Продукты", "Транспорт / Авто", "Кафе", "Услуги / Красота", "Здоровье", "Развлечения")
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
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) { Text("Записать расход") }
    }
}
}

// --- ВКЛАДКА 2: ИСТОРИЯ ---
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

// Диалог удаления/редактирования
if (editExpense != null) {
    AlertDialog(
        onDismissRequest = { editExpense = null },
        title = { Text("Управление расходом") },
        text = { Text("Удалить этот расход? Лимиты пересчитаются автоматически.") },
        confirmButton = {
            Button(onClick = {
                onUpdate(expenses.filter { it.id != editExpense!!.id })
                editExpense = null
            }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Удалить") }
        },
        dismissButton = { Button(onClick = { editExpense = null }) { Text("Отмена") } }
    )
}
}

// --- ВКЛАДКА 3: НАСТРОЙКИ МЕСЯЦЕВ ---
@Composable
fun MonthsScreen(budgets: Map<YearMonth, Int>, onUpdate: (Map<YearMonth, Int>) -> Unit) {
    var newLimit by remember { mutableStateOf("") }
    var selectedMonth by remember { mutableStateOf(YearMonth.now().monthValue) }
    var selectedYear by remember { mutableStateOf(YearMonth.now().year) }

Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
    Text("Управление лимитами", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))
    
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Новый лимит", fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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

    Text("Настроенные месяцы:", fontWeight = FontWeight.Bold)
    LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
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
