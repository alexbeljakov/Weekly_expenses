package com.composables.jetpackcomposetemplate

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        expenses.forEach { e -> array.put(JSONObject().apply { put("id", e.id); put("amount", e.amount); put("place", e.place); put("category", e.category); put("date", e.date.toString()) }) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("expenses", array.toString()).apply()
    }
    fun loadExpenses(context: Context): List<Expense> = try {
        val array = JSONArray(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("expenses", "[]") ?: "[]")
        List(array.length()) { i -> val obj = array.getJSONObject(i); Expense(obj.getString("id"), obj.getInt("amount"), obj.getString("place"), obj.getString("category"), LocalDate.parse(obj.getString("date"))) }
    } catch (e: Exception) { emptyList() }

    fun savePlanned(context: Context, planned: List<PlannedExpense>) {
        val array = JSONArray()
        planned.forEach { p -> array.put(JSONObject().apply { put("id", p.id); put("monthStr", p.monthStr); put("name", p.name); put("amount", p.amount) }) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("planned", array.toString()).apply()
    }
    fun loadPlanned(context: Context): List<PlannedExpense> = try {
        val array = JSONArray(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("planned", "[]") ?: "[]")
        List(array.length()) { i -> val obj = array.getJSONObject(i); PlannedExpense(obj.getString("id"), obj.getString("monthStr"), obj.getString("name"), obj.getInt("amount")) }
    } catch (e: Exception) { emptyList() }

    fun saveBudgets(context: Context, budgets: Map<YearMonth, Int>) {
        val obj = JSONObject()
        budgets.forEach { (ym, limit) -> obj.put(ym.toString(), limit) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("budgets", obj.toString()).apply()
    }
    fun loadBudgets(context: Context): Map<YearMonth, Int> = try {
        val obj = JSONObject(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("budgets", "{}") ?: "{}")
        val map = mutableMapOf<YearMonth, Int>()
        obj.keys().forEach { key -> map[YearMonth.parse(key)] = obj.getInt(key) }
        map
    } catch (e: Exception) { emptyMap() }

    fun saveRollover(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("rollover", enabled).apply()
    }
    fun loadRollover(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("rollover", true)
    }
}

// --- ТЕМА Т-БАНКА ---
private val TBankColorScheme = darkColorScheme(
    primary = Color(0xFFFFDD2D), // Фирменный желтый
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF222224), // Темно-серые карточки
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFFFDD2D),
    background = Color(0xFF121212), // Почти черный фон
    surface = Color(0xFF222224),
    error = Color(0xFFFF453A) // Красный
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = TBankColorScheme) { BudgetApp() } }
    }
}

// --- ОСНОВНАЯ НАВИГАЦИЯ ---
@Composable
fun BudgetApp() {
    val context = LocalContext.current
    var expenses by remember { mutableStateOf(Storage.loadExpenses(context)) }
    var planned by remember { mutableStateOf(Storage.loadPlanned(context)) }
    var budgets by remember { mutableStateOf(Storage.loadBudgets(context)) }
    var rolloverEnabled by remember { mutableStateOf(Storage.loadRollover(context)) }
    
    var currentTab by remember { mutableStateOf(0) }
    var showAddSheet by remember { mutableStateOf(false) }

    LaunchedEffect(expenses) { Storage.saveExpenses(context, expenses) }
    LaunchedEffect(planned) { Storage.savePlanned(context, planned) }
    LaunchedEffect(budgets) { Storage.saveBudgets(context, budgets) }
    LaunchedEffect(rolloverEnabled) { Storage.saveRollover(context, rolloverEnabled) }

    Scaffold(
        floatingActionButton = {
            if (currentTab == 0) {
                FloatingActionButton(
                    onClick = { showAddSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape
                ) { Icon(Icons.Default.Add, "Добавить расход", modifier = Modifier.size(28.dp)) }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(icon = { Icon(Icons.Default.Home, "") }, label = { Text("Главная") }, selected = currentTab == 0, onClick = { currentTab = 0 })
                NavigationBarItem(icon = { Icon(Icons.Default.Info, "") }, label = { Text("Сводка") }, selected = currentTab == 1, onClick = { currentTab = 1 })
                NavigationBarItem(icon = { Icon(Icons.Default.List, "") }, label = { Text("История") }, selected = currentTab == 2, onClick = { currentTab = 2 })
                NavigationBarItem(icon = { Icon(Icons.Default.DateRange, "") }, label = { Text("Бюджет") }, selected = currentTab == 3, onClick = { currentTab = 3 })
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (currentTab) {
                0 -> HomeScreen(expenses, planned, budgets, rolloverEnabled, onToggleRollover = { rolloverEnabled = it }, onDeletePlanned = { id -> planned = planned.filter { it.id != id } })
                1 -> AnalyticsScreen(expenses)
                2 -> HistoryScreen(expenses, onUpdate = { expenses = it })
                3 -> MonthsScreen(budgets, onUpdate = { budgets = it })
            }

            // КАСТОМНАЯ ШТОРКА ДОБАВЛЕНИЯ (Без багов с клавиатурой)
            AnimatedVisibility(
                visible = showAddSheet,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Затемнение фона
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showAddSheet = false })
                    
                    // Сама карточка
                    Card(
                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        AddExpenseSheet(
                            onAdd = { exp -> expenses = expenses + exp; showAddSheet = false },
                            onAddPlanned = { p -> planned = planned + p; showAddSheet = false }
                        )
                    }
                }
            }
        }
    }
}

// --- ВКЛАДКА 1: ДАШБОРД ---
@Composable
fun HomeScreen(expenses: List<Expense>, planned: List<PlannedExpense>, budgets: Map<YearMonth, Int>, 
               rolloverEnabled: Boolean, onToggleRollover: (Boolean) -> Unit, onDeletePlanned: (String) -> Unit) {
    var monthOffset by remember { mutableStateOf(0) }
    val displayMonth = YearMonth.now().plusMonths(monthOffset.toLong())
    val totalLimit = budgets[displayMonth]
    val today = LocalDate.now()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp).pointerInput(Unit) {
                detectHorizontalDragGestures(onDragEnd = {}) { change, dragAmount ->
                    change.consume(); if (dragAmount > 20) monthOffset -= 1 else if (dragAmount < -20) monthOffset += 1
                }
            }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monthOffset -= 1 }) { Icon(Icons.Default.KeyboardArrowLeft, "Назад", tint = Color.Gray) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(displayMonth.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Перенос остатка", fontSize = 12.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(checked = rolloverEnabled, onCheckedChange = onToggleRollover, modifier = Modifier.scale(0.7f), colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)))
                    }
                }
                IconButton(onClick = { monthOffset += 1 }) { Icon(Icons.Default.KeyboardArrowRight, "Вперед", tint = Color.Gray) }
            }
        }

        if (totalLimit == null) {
            item { Text("Бюджет не задан. Настройте во вкладке 'Бюджет'.", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        } else {
            val daysInMonth = displayMonth.lengthOfMonth()
            val plannedForMonth = planned.filter { it.monthStr == displayMonth.toString() }.sumOf { it.amount }
            val remainingForWeeks = totalLimit - plannedForMonth
            val dailyLimit = if (remainingForWeeks > 0) remainingForWeeks.toDouble() / daysInMonth else 0.0
            
            var currentStart = displayMonth.atDay(1)
            var carryover = 0
            val weeks = mutableListOf<WeekPeriod>()
            
            while (currentStart.month == displayMonth.month) {
                var currentEnd = currentStart
                while (currentEnd.dayOfWeek.value != 7 && currentEnd.dayOfMonth < daysInMonth) { currentEnd = currentEnd.plusDays(1) }
                
                val daysInWeek = currentEnd.dayOfMonth - currentStart.dayOfMonth + 1
                val baseWeekLimit = (dailyLimit * daysInWeek).roundToInt()
                val actualLimit = if (rolloverEnabled) baseWeekLimit + carryover else baseWeekLimit
                val spent = expenses.filter { it.date in currentStart..currentEnd }.sumOf { it.amount }
                
                val remaining = actualLimit - spent
                if (rolloverEnabled) { carryover = remaining } 
                
                weeks.add(WeekPeriod(currentStart, currentEnd, actualLimit, spent))
                currentStart = currentEnd.plusDays(1)
            }

            val currentWeek = weeks.find { today in it.start..it.end }
            if (currentWeek != null && displayMonth == YearMonth.now()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("ОСТАЛОСЬ НА ЭТОЙ НЕДЕЛЕ", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text("${currentWeek.remaining} ₽", fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            Text("до ${currentWeek.end.format(DateTimeFormatter.ofPattern("dd.MM"))}", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }

            items(weeks) { week ->
                val progress = if (week.limit > 0) (week.spent.toFloat() / week.limit.toFloat()).coerceIn(0f, 1f) else 1f
                val isOverspent = week.remaining < 0
                val barColor = if (isOverspent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${week.start.dayOfMonth}.${week.start.monthValue} - ${week.end.dayOfMonth}.${week.end.monthValue}", fontWeight = FontWeight.Bold, color = Color.White)
                        Text("${week.remaining} ₽", fontWeight = FontWeight.Bold, color = if (isOverspent) MaterialTheme.colorScheme.error else Color.White)
                    }
                    LinearProgressIndicator(progress = progress, color = barColor, trackColor = Color(0xFF333333), modifier = Modifier.fillMaxWidth().height(8.dp).padding(top = 6.dp).clip(RoundedCornerShape(4.dp)))
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Траты: ${week.spent} ₽", fontSize = 12.sp, color = Color.Gray)
                        Text("Лимит: ${week.limit} ₽", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }

        item {
            val monthPlanned = planned.filter { it.monthStr == displayMonth.toString() }
            if (monthPlanned.isNotEmpty()) {
                Text("Отложенные траты (вычтено)", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
                monthPlanned.forEach { p ->
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("📦 ${p.name}", color = Color.White, fontWeight = FontWeight.Medium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${p.amount} ₽", color = Color.White, modifier = Modifier.padding(end = 16.dp))
                                Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error, modifier = Modifier.clickable { onDeletePlanned(p.id) })
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

// --- КОМПОНЕНТ: ШТОРКА ДОБАВЛЕНИЯ С ДИЗАЙНОМ Т-БАНКА ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseSheet(onAdd: (Expense) -> Unit, onAddPlanned: (PlannedExpense) -> Unit) {
    var isPlanned by remember { mutableStateOf(false) }
    var amountInput by remember { mutableStateOf("") }
    var placeInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("🛒 Продукты") }
    val categories = listOf("🛒 Продукты", "🚗 Транспорт", "🍔 Кафе", "✂ Услуги", "💊 Здоровье", "🍿 Развлечения", "🏠 Дом", "📦 Иное")

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedContainerColor = Color(0xFF2C2C2E),
        unfocusedContainerColor = Color(0xFF2C2C2E),
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = Color.Transparent,
        cursorColor = MaterialTheme.colorScheme.primary
    )

    Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (isPlanned) "Отложенная трата" else "Новый расход", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Отложить", fontSize = 12.sp, color = Color.Gray)
                Switch(checked = isPlanned, onCheckedChange = { isPlanned = it }, modifier = Modifier.padding(start = 8.dp).scale(0.8f), colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)))
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = amountInput, onValueChange = { amountInput = it }, label = { Text("Сумма (₽)", color = Color.Gray) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = textFieldColors)
        OutlinedTextField(value = placeInput, onValueChange = { placeInput = it }, label = { Text(if (isPlanned) "Название (Например: КАСКО)" else "Место или комментарий", color = Color.Gray) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(12.dp), colors = textFieldColors)
        
        if (!isPlanned) {
            var expanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp), border = null, colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF2C2C2E))) { 
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) { Text("Категория: $selectedCategory", color = Color.White) } 
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                    categories.forEach { cat -> DropdownMenuItem(text = { Text(cat, color = Color.White) }, onClick = { selectedCategory = cat; expanded = false }) }
                }
            }
        }
        
        Button(
            onClick = {
                val amt = amountInput.toIntOrNull() ?: 0
                if (amt > 0) {
                    if (isPlanned) onAddPlanned(PlannedExpense(UUID.randomUUID().toString(), YearMonth.now().toString(), placeInput, amt))
                    else onAdd(Expense(UUID.randomUUID().toString(), amt, placeInput, selectedCategory, LocalDate.now()))
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp).height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) { Text("СОХРАНИТЬ", fontWeight = FontWeight.Bold, color = Color.Black) }
    }
}

// --- ВКЛАДКА 2: АНАЛИТИКА ---
@Composable
fun AnalyticsScreen(expenses: List<Expense>) {
    var monthOffset by remember { mutableStateOf(0) }
    val displayMonth = YearMonth.now().plusMonths(monthOffset.toLong())
    val monthExpenses = expenses.filter { YearMonth.from(it.date) == displayMonth }
    val totalSpent = monthExpenses.sumOf { it.amount }
    val grouped = monthExpenses.groupBy { it.category }.mapValues { it.value.sumOf { e -> e.amount } }.toList().sortedByDescending { it.second }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { monthOffset -= 1 }) { Icon(Icons.Default.KeyboardArrowLeft, "Назад", tint = Color.Gray) }
            Text("Сводка за $displayMonth", style = MaterialTheme.typography.titleMedium, color = Color.White)
            IconButton(onClick = { monthOffset += 1 }) { Icon(Icons.Default.KeyboardArrowRight, "Вперед", tint = Color.Gray) }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Всего потрачено", fontSize = 14.sp, color = Color.Gray)
                    Text("$totalSpent ₽", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }

            LazyColumn {
                items(grouped) { (cat, sum) ->
                    val progress = if (totalSpent > 0) sum.toFloat() / totalSpent.toFloat() else 0f
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(cat, fontWeight = FontWeight.Medium, color = Color.White)
                            Text("$sum ₽", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        LinearProgressIndicator(progress = progress, color = MaterialTheme.colorScheme.primary, trackColor = Color(0xFF333333), modifier = Modifier.fillMaxWidth().height(8.dp).padding(top = 6.dp).clip(RoundedCornerShape(4.dp)))
                    }
                }
            }
        }
    }
}

// --- ВКЛАДКА 3: ИСТОРИЯ ---
@Composable
fun HistoryScreen(expenses: List<Expense>, onUpdate: (List<Expense>) -> Unit) {
    var monthOffset by remember { mutableStateOf(0) }
    val displayMonth = YearMonth.now().plusMonths(monthOffset.toLong())
    val monthExpenses = expenses.filter { YearMonth.from(it.date) == displayMonth }
    
    var editExpense by remember { mutableStateOf<Expense?>(null) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { monthOffset -= 1 }) { Icon(Icons.Default.KeyboardArrowLeft, "Назад", tint = Color.Gray) }
            Text("История за $displayMonth", style = MaterialTheme.typography.titleMedium, color = Color.White)
            IconButton(onClick = { monthOffset += 1 }) { Icon(Icons.Default.KeyboardArrowRight, "Вперед", tint = Color.Gray) }
        }

        LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
            items(monthExpenses.sortedByDescending { it.date }) { exp ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { editExpense = exp }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(exp.category, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("${exp.place} • ${exp.date.format(DateTimeFormatter.ofPattern("dd.MM"))}", fontSize = 12.sp, color = Color.Gray)
                        }
                        Text("-${exp.amount} ₽", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
    
    if (editExpense != null) {
        AlertDialog(
            onDismissRequest = { editExpense = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Удалить расход?", color = Color.White) },
            text = { Text("Деньги автоматически вернутся в лимит соответствующей недели.", color = Color.Gray) },
            confirmButton = { Button(onClick = { onUpdate(expenses.filter { it.id != editExpense!!.id }); editExpense = null }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Удалить", color = Color.White) } },
            dismissButton = { OutlinedButton(onClick = { editExpense = null }) { Text("Отмена", color = Color.White) } }
        )
    }
}

// --- ВКЛАДКА 4: НАСТРОЙКИ ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthsScreen(budgets: Map<YearMonth, Int>, onUpdate: (Map<YearMonth, Int>) -> Unit) {
    var newLimit by remember { mutableStateOf("") }
    var selectedMonth by remember { mutableStateOf(YearMonth.now().monthValue) }
    var selectedYear by remember { mutableStateOf(YearMonth.now().year) }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
        focusedContainerColor = Color(0xFF2C2C2E), unfocusedContainerColor = Color(0xFF2C2C2E),
        focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.Transparent
    )

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("Настройка бюджета", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(bottom = 16.dp))
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Установить новый лимит", fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(bottom = 12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = selectedMonth.toString(), onValueChange = { selectedMonth = it.toIntOrNull() ?: 1 }, label = { Text("Месяц (1-12)", color = Color.Gray) }, modifier = Modifier.weight(1f).padding(end = 4.dp), shape = RoundedCornerShape(12.dp), colors = textFieldColors)
                    OutlinedTextField(value = selectedYear.toString(), onValueChange = { selectedYear = it.toIntOrNull() ?: 2026 }, label = { Text("Год", color = Color.Gray) }, modifier = Modifier.weight(1f).padding(start = 4.dp), shape = RoundedCornerShape(12.dp), colors = textFieldColors)
                }
                OutlinedTextField(value = newLimit, onValueChange = { newLimit = it }, label = { Text("Сумма на месяц (₽)", color = Color.Gray) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(12.dp), colors = textFieldColors)
                Button(onClick = {
                    val limitInt = newLimit.toIntOrNull()
                    if (limitInt != null && selectedMonth in 1..12) {
                        val newBudgets = budgets.toMutableMap()
                        newBudgets[YearMonth.of(selectedYear, selectedMonth)] = limitInt
                        onUpdate(newBudgets)
                        newLimit = ""
                    }
                }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(50.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("СОХРАНИТЬ ЛИМИТ", fontWeight = FontWeight.Bold, color = Color.Black) }
            }
        }
        Text("Сохраненные лимиты", fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
        LazyColumn {
            items(budgets.toList().sortedByDescending { it.first }) { (ym, limit) ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(ym.toString(), fontWeight = FontWeight.Bold, color = Color.White)
                        Text("$limit ₽", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
