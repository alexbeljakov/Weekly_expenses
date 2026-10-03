package com.composables.jetpackcomposetemplate

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToInt

// --- МОДЕЛИ ДАННЫХ ---
data class Expense(val id: String, val amount: Double, val place: String, val category: String, val date: LocalDate)
data class PlannedExpense(val id: String, val monthStr: String, val name: String, val amount: Double)
data class WeekPeriod(val start: LocalDate, val end: LocalDate, val limit: Double, val spent: Double) {
    val remaining get() = limit - spent
}

// --- ФОРМАТИРОВАНИЕ ДЕНЕГ (С ПРОБЕЛАМИ) ---
fun getMoneyFormatter(): DecimalFormat {
    val symbols = DecimalFormatSymbols().apply { groupingSeparator = ' ' }
    return DecimalFormat("#,##0", symbols)
}

fun formatMoneyFull(amount: Double): androidx.compose.ui.text.AnnotatedString {
    val formatter = getMoneyFormatter()
    return buildAnnotatedString {
        val whole = amount.toLong()
        val fraction = ((amount - whole) * 100).roundToInt().coerceIn(0, 99)
        append(formatter.format(whole))
        if (fraction > 0) {
            withStyle(style = SpanStyle(color = Color.Gray, fontSize = 12.sp)) { append(".${fraction.toString().padStart(2, '0')}") }
        }
        append(" ₽")
    }
}
fun formatMoneyWhole(amount: Double): String = "${getMoneyFormatter().format(amount)} ₽"

// --- СОХРАНЕНИЕ ДАННЫХ И БЭКАП ---
object Storage {
    private const val PREFS_NAME = "BudgetPrefs"
    private val DEFAULT_CATEGORIES = listOf("🛒 Продукты", "🚗 Транспорт", "🍔 Кафе", "✂ Услуги", "💊 Здоровье", "🍿 Развлечения", "🏠 Дом", "📦 Иное")

    fun saveExpenses(context: Context, expenses: List<Expense>) {
        val array = JSONArray()
        expenses.forEach { e -> array.put(JSONObject().apply { put("id", e.id); put("amount", e.amount); put("place", e.place); put("category", e.category); put("date", e.date.toString()) }) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("expenses", array.toString()).apply()
    }
    fun loadExpenses(context: Context): List<Expense> = try {
        val array = JSONArray(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("expenses", "[]") ?: "[]")
        List(array.length()) { i -> 
            val obj = array.getJSONObject(i)
            val amt = if (obj.get("amount") is Int) obj.getInt("amount").toDouble() else obj.optDouble("amount", 0.0)
            Expense(obj.getString("id"), amt, obj.getString("place"), obj.getString("category"), LocalDate.parse(obj.getString("date"))) 
        }
    } catch (e: Exception) { emptyList() }

    fun savePlanned(context: Context, planned: List<PlannedExpense>) {
        val array = JSONArray()
        planned.forEach { p -> array.put(JSONObject().apply { put("id", p.id); put("monthStr", p.monthStr); put("name", p.name); put("amount", p.amount) }) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("planned", array.toString()).apply()
    }
    fun loadPlanned(context: Context): List<PlannedExpense> = try {
        val array = JSONArray(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("planned", "[]") ?: "[]")
        List(array.length()) { i -> 
            val obj = array.getJSONObject(i)
            val amt = if (obj.get("amount") is Int) obj.getInt("amount").toDouble() else obj.optDouble("amount", 0.0)
            PlannedExpense(obj.getString("id"), obj.getString("monthStr"), obj.getString("name"), amt) 
        }
    } catch (e: Exception) { emptyList() }

    fun saveBudgets(context: Context, budgets: Map<YearMonth, Double>) {
        val obj = JSONObject()
        budgets.forEach { (ym, limit) -> obj.put(ym.toString(), limit) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("budgets", obj.toString()).apply()
    }
    fun loadBudgets(context: Context): Map<YearMonth, Double> = try {
        val obj = JSONObject(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("budgets", "{}") ?: "{}")
        val map = mutableMapOf<YearMonth, Double>()
        obj.keys().forEach { key -> map[YearMonth.parse(key)] = obj.getDouble(key) }
        map
    } catch (e: Exception) { emptyMap() }

    fun saveCategories(context: Context, cats: List<String>) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("cats", JSONArray(cats).toString()).apply()
    fun loadCategories(context: Context): List<String> = try {
        val str = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("cats", null)
        if (str == null) DEFAULT_CATEGORIES else { val arr = JSONArray(str); List(arr.length()) { arr.getString(it) } }
    } catch (e: Exception) { DEFAULT_CATEGORIES }

    fun saveRollover(context: Context, enabled: Boolean) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("rollover", enabled).apply()
    fun loadRollover(context: Context): Boolean = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("rollover", true)

    fun exportAll(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val exportObj = JSONObject()
        exportObj.put("expenses", JSONArray(prefs.getString("expenses", "[]")))
        exportObj.put("planned", JSONArray(prefs.getString("planned", "[]")))
        exportObj.put("budgets", JSONObject(prefs.getString("budgets", "{}")))
        exportObj.put("cats", JSONArray(prefs.getString("cats", JSONArray(DEFAULT_CATEGORIES).toString())))
        exportObj.put("rollover", prefs.getBoolean("rollover", true))
        return exportObj.toString()
    }

    fun importAll(context: Context, jsonString: String): Boolean {
        return try {
            val obj = JSONObject(jsonString)
            val edit = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            if (obj.has("expenses")) edit.putString("expenses", obj.getJSONArray("expenses").toString())
            if (obj.has("planned")) edit.putString("planned", obj.getJSONArray("planned").toString())
            if (obj.has("budgets")) edit.putString("budgets", obj.getJSONObject("budgets").toString())
            if (obj.has("cats")) edit.putString("cats", obj.getJSONArray("cats").toString())
            if (obj.has("rollover")) edit.putBoolean("rollover", obj.getBoolean("rollover"))
            edit.apply()
            true
        } catch (e: Exception) { false }
    }
}

// --- ТЕМА Т-БАНКА ---
private val TBankColorScheme = darkColorScheme(
    primary = Color(0xFFFFDD2D), onPrimary = Color.Black,
    primaryContainer = Color(0xFF222224), onPrimaryContainer = Color.White,
    secondary = Color(0xFFFFDD2D), background = Color(0xFF121212),
    surface = Color(0xFF222224), error = Color(0xFFFF453A)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Разрешаем рисовать приложение под системными панелями
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        // 2. Делаем панели полностью прозрачными (теперь они подстроятся под любую тему)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        
        setContent { MaterialTheme(colorScheme = TBankColorScheme) { BudgetApp() } }
    }
}


// --- ОСНОВНАЯ НАВИГАЦИЯ (PAGER) ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BudgetApp() {
    val context = LocalContext.current
    var reloadTrigger by remember { mutableStateOf(0) }
    
    var expenses by remember(reloadTrigger) { mutableStateOf(Storage.loadExpenses(context)) }
    var planned by remember(reloadTrigger) { mutableStateOf(Storage.loadPlanned(context)) }
    var budgets by remember(reloadTrigger) { mutableStateOf(Storage.loadBudgets(context)) }
    var categories by remember(reloadTrigger) { mutableStateOf(Storage.loadCategories(context)) }
    var rolloverEnabled by remember(reloadTrigger) { mutableStateOf(Storage.loadRollover(context)) }
    
    val pagerState = rememberPagerState() // pageCount перенесли ниже
    val coroutineScope = rememberCoroutineScope()
    
    var showAddSheet by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var historyFilters by remember { mutableStateOf<Set<String>>(emptySet()) }
    var historySearchQuery by remember { mutableStateOf("") }
    
    var expenseToEdit by remember { mutableStateOf<Expense?>(null) }
    var plannedToEdit by remember { mutableStateOf<PlannedExpense?>(null) }

    val uniquePlaces = remember(expenses) { expenses.map { it.place }.distinct().filter { it.isNotBlank() } }

    LaunchedEffect(expenses) { Storage.saveExpenses(context, expenses) }
    LaunchedEffect(planned) { Storage.savePlanned(context, planned) }
    LaunchedEffect(budgets) { Storage.saveBudgets(context, budgets) }
    LaunchedEffect(categories) { Storage.saveCategories(context, categories) }
    LaunchedEffect(rolloverEnabled) { Storage.saveRollover(context, rolloverEnabled) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            containerColor = Color.Transparent, // Чтобы фулскрин работал корректно
            floatingActionButton = {
                AnimatedVisibility(visible = pagerState.currentPage == 0, enter = scaleIn(), exit = scaleOut()) {
                    FloatingActionButton(onClick = { expenseToEdit = null; plannedToEdit = null; showAddSheet = true }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = CircleShape) { 
                        Icon(Icons.Default.Add, "Добавить", modifier = Modifier.size(28.dp)) 
                    }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(icon = { Icon(Icons.Default.Home, "") }, label = { Text("Главная") }, selected = pagerState.currentPage == 0, onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } })
                    NavigationBarItem(icon = { Icon(Icons.Default.Info, "") }, label = { Text("Сводка") }, selected = pagerState.currentPage == 1, onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } })
                    NavigationBarItem(icon = { Icon(Icons.Default.List, "") }, label = { Text("История") }, selected = pagerState.currentPage == 2, onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } })
                    NavigationBarItem(icon = { Icon(Icons.Default.Settings, "") }, label = { Text("Настройки") }, selected = pagerState.currentPage == 3, onClick = { coroutineScope.launch { pagerState.animateScrollToPage(3) } })
                }
            }
        ) { padding ->
            HorizontalPager(
                pageCount = 4, 
                state = pagerState, 
                modifier = Modifier.padding(padding).fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> HomeScreen(expenses, planned, budgets, rolloverEnabled, 
                            onNavigateToSettings = { coroutineScope.launch { pagerState.animateScrollToPage(3) } },
                            onToggleRollover = { rolloverEnabled = it }, 
                            onEditPlanned = { p -> plannedToEdit = p; expenseToEdit = null; showAddSheet = true }, 
                            onEditExpense = { e -> expenseToEdit = e; plannedToEdit = null; showAddSheet = true })
                    1 -> AnalyticsScreen(expenses)
                    2 -> HistoryScreen(expenses, historyFilters, historySearchQuery, onOpenFilter = { showFilterSheet = true }, onEdit = { e -> expenseToEdit = e; plannedToEdit = null; showAddSheet = true })
                    3 -> SettingsScreen(budgets, categories, 
                            onUpdateBudgets = { budgets = it }, 
                            onUpdateCategories = { old, new, newList -> 
                                categories = newList
                                if (old != null && old != new) {
                                    val replacement = new ?: "📦 Иное"
                                    expenses = expenses.map { if (it.category == old) it.copy(category = replacement) else it }
                                }
                            },
                            onImportSuccess = { reloadTrigger++ }
                        )
                }
            }
        }

        // --- УМНЫЕ ШТОРКИ ---
        AnimatedVisibility(visible = showAddSheet || showFilterSheet, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showAddSheet = false; showFilterSheet = false })
        }

        AnimatedVisibility(visible = showAddSheet, enter = slideInVertically(initialOffsetY = { it }), exit = slideOutVertically(targetOffsetY = { it }), modifier = Modifier.align(Alignment.BottomCenter)) {
            var offsetY by remember { mutableStateOf(0f) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { androidx.compose.ui.unit.IntOffset(0, offsetY.roundToInt()) }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (offsetY > 200f) {
                                    showAddSheet = false // (или showFilterSheet для второй шторки)
                                } else {
                                    offsetY = 0f // Возвращаем на место только если свайп был слишком слабым
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                if (dragAmount > 0 || offsetY > 0) {
                                    offsetY = (offsetY + dragAmount).coerceAtLeast(0f)
                                }
                            }
                        )
                    }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}, 
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), 
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                // Индикатор свайпа (серая таблетка)
                Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(Color.DarkGray))
                }
                AddExpenseSheet(
                    categories = categories,
                    uniquePlaces = uniquePlaces,
                    initialExpense = expenseToEdit,
                    initialPlanned = plannedToEdit,
                    onSaveExpense = { exp -> expenses = expenses.filter { it.id != exp.id } + exp; showAddSheet = false },
                    onSavePlanned = { p -> planned = planned.filter { it.id != p.id } + p; showAddSheet = false },
                    onDeleteExpense = { id -> expenses = expenses.filter { it.id != id }; showAddSheet = false },
                    onDeletePlanned = { id -> planned = planned.filter { it.id != id }; showAddSheet = false }
                )
            }
        }

        AnimatedVisibility(visible = showFilterSheet, enter = slideInVertically(initialOffsetY = { it }), exit = slideOutVertically(targetOffsetY = { it }), modifier = Modifier.align(Alignment.BottomCenter)) {
            var offsetY by remember { mutableStateOf(0f) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { androidx.compose.ui.unit.IntOffset(0, offsetY.roundToInt()) }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (offsetY > 200f) {
                                    showAddSheet = false // (или showFilterSheet для второй шторки)
                                } else {
                                    offsetY = 0f // Возвращаем на место только если свайп был слишком слабым
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                if (dragAmount > 0 || offsetY > 0) {
                                    offsetY = (offsetY + dragAmount).coerceAtLeast(0f)
                                }
                            }
                        )
                    }                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}, 
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), 
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(Color.DarkGray))
                }
                FilterSheet(categories = categories, uniquePlaces = uniquePlaces, selected = historyFilters, searchQuery = historySearchQuery, onApply = { newFilters, newSearch -> historyFilters = newFilters; historySearchQuery = newSearch; showFilterSheet = false })
            }
        }
    }
}

// --- ВКЛАДКА 1: ДАШБОРД ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(expenses: List<Expense>, planned: List<PlannedExpense>, budgets: Map<YearMonth, Double>, rolloverEnabled: Boolean, 
               onNavigateToSettings: () -> Unit, onToggleRollover: (Boolean) -> Unit, onEditPlanned: (PlannedExpense) -> Unit, onEditExpense: (Expense) -> Unit) {
    var monthOffset by remember { mutableStateOf(0) }
    var swiped by remember { mutableStateOf(false) } 
    
    val displayMonth = YearMonth.now().plusMonths(monthOffset.toLong())
    val totalLimit = budgets[displayMonth]
    val today = LocalDate.now()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp).pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { swiped = false },
                    onDragEnd = { swiped = false },
                    onDragCancel = { swiped = false },
                    onHorizontalDrag = { change, dragAmount -> 
                        change.consume()
                        if (!swiped) {
                            if (dragAmount > 50) { monthOffset -= 1; swiped = true }
                            else if (dragAmount < -50) { monthOffset += 1; swiped = true }
                        }
                    }
                )
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
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("💳", fontSize = 56.sp, modifier = Modifier.padding(bottom = 16.dp))
                        Text("Бюджет не задан", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Установите лимит на этот месяц, чтобы начать контролировать свои расходы.", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
                        Button(onClick = onNavigateToSettings, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                            Text("НАСТРОИТЬ БЮДЖЕТ", fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }
        } else {
            val daysInMonth = displayMonth.lengthOfMonth()
            val plannedForMonth = planned.filter { it.monthStr == displayMonth.toString() }.sumOf { it.amount }
            val spentInMonth = expenses.filter { YearMonth.from(it.date) == displayMonth }.sumOf { it.amount }
            val remainingMonth = totalLimit - plannedForMonth - spentInMonth
            
            val remainingForWeeks = totalLimit - plannedForMonth
            val dailyLimit = if (remainingForWeeks > 0) remainingForWeeks / daysInMonth else 0.0
            
            var currentStart = displayMonth.atDay(1)
            var carryover = 0.0
            val weeks = mutableListOf<WeekPeriod>()
            
            while (currentStart.month == displayMonth.month) {
                var currentEnd = currentStart
                while (currentEnd.dayOfWeek.value != 7 && currentEnd.dayOfMonth < daysInMonth) { currentEnd = currentEnd.plusDays(1) }
                val daysInWeek = currentEnd.dayOfMonth - currentStart.dayOfMonth + 1
                val baseWeekLimit = dailyLimit * daysInWeek
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
                    val heroPagerState = rememberPagerState() // Исправили Pager
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        HorizontalPager(pageCount = 2, state = heroPagerState, modifier = Modifier.fillMaxWidth()) { page ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(20.dp), 
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    if (page == 0) {
                                        Text("ОСТАЛОСЬ НА ЭТОЙ НЕДЕЛЕ", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                        Text(formatMoneyWhole(currentWeek.remaining), fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = if (currentWeek.remaining < 0) MaterialTheme.colorScheme.error else Color.White)
                                        Text("до ${currentWeek.end.format(DateTimeFormatter.ofPattern("dd.MM"))}", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                                    } else {
                                        Text("ОСТАЛОСЬ ДО КОНЦА МЕСЯЦА", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                        Text(formatMoneyWhole(remainingMonth), fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = if (remainingMonth < 0) MaterialTheme.colorScheme.error else Color.White)
                                        Text("Общий лимит: ${formatMoneyWhole(totalLimit)}", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                            }
                        }
                        Row(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.Center) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (heroPagerState.currentPage == 0) Color.White else Color.DarkGray))
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (heroPagerState.currentPage == 1) Color.White else Color.DarkGray))
                        }
                    }
                }
            }

            items(weeks) { week ->
                val progress = if (week.limit > 0) (week.spent / week.limit).toFloat().coerceIn(0f, 1f) else 1f
                val isOverspent = week.remaining < 0
                val barColor = if (isOverspent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${week.start.dayOfMonth}.${week.start.monthValue} - ${week.end.dayOfMonth}.${week.end.monthValue}", fontWeight = FontWeight.Bold, color = Color.White)
                        Text(formatMoneyWhole(week.remaining), fontWeight = FontWeight.Bold, color = if (isOverspent) MaterialTheme.colorScheme.error else Color.White)
                    }
                    LinearProgressIndicator(progress = progress, color = barColor, trackColor = Color(0xFF333333), modifier = Modifier.fillMaxWidth().height(8.dp).padding(top = 6.dp).clip(RoundedCornerShape(4.dp)))
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Траты: ${formatMoneyWhole(week.spent)}", fontSize = 12.sp, color = Color.Gray)
                        Text("Лимит: ${formatMoneyWhole(week.limit)}", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }

        item {
            val monthPlanned = planned.filter { it.monthStr == displayMonth.toString() }
            if (monthPlanned.isNotEmpty()) {
                Text("Отложенные траты (вычтено)", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
                monthPlanned.forEach { p ->
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { onEditPlanned(p) }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("📦 ${p.name}", color = Color.White, fontWeight = FontWeight.Medium)
                            Text(formatMoneyFull(p.amount), color = Color.White)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

// --- КОМПОНЕНТ: ШТОРКА ФИЛЬТРА ИСТОРИИ (СМАРТ-ПОИСК) ---
@Composable
fun FilterSheet(categories: List<String>, uniquePlaces: List<String>, selected: Set<String>, searchQuery: String, onApply: (Set<String>, String) -> Unit) {
    var currentSelection by remember { mutableStateOf(selected) }
    var currentSearch by remember { mutableStateOf(searchQuery) }
    var showSuggestions by remember { mutableStateOf(false) }
    
    val filteredPlaces = uniquePlaces.filter { it.contains(currentSearch, ignoreCase = true) && it != currentSearch }.take(3)
    val tfColors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = Color(0xFF2C2C2E), unfocusedContainerColor = Color(0xFF2C2C2E), focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.Transparent, cursorColor = MaterialTheme.colorScheme.primary)

    Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Фильтр истории", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
            if (currentSelection.isNotEmpty() || currentSearch.isNotBlank()) {
                Text("Сбросить", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.clickable { currentSelection = emptySet(); currentSearch = "" })
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = currentSearch,
            onValueChange = { currentSearch = it; showSuggestions = it.isNotBlank() },
            label = { Text("Поиск по месту", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth().onFocusChanged { showSuggestions = it.isFocused && currentSearch.isNotBlank() },
            shape = RoundedCornerShape(12.dp), colors = tfColors
        )
        AnimatedVisibility(visible = showSuggestions && filteredPlaces.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
                Column {
                    filteredPlaces.forEach { place ->
                        Text(place, color = Color.White, modifier = Modifier.fillMaxWidth().clickable { currentSearch = place; showSuggestions = false }.padding(12.dp))
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        val columns = 3
        Column {
            categories.chunked(columns).forEach { rowCats ->
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowCats.forEach { cat ->
                        val isSelected = currentSelection.contains(cat)
                        Box(modifier = Modifier.weight(1f).height(52.dp).clickable { 
                            currentSelection = if (isSelected) currentSelection - cat else currentSelection + cat 
                        }.background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color(0xFF2C2C2E), RoundedCornerShape(12.dp)).border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp)).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                            Text(cat, color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    repeat(columns - rowCats.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        
        Button(
            onClick = { onApply(currentSelection, currentSearch) },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp).height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) { Text("ПОКАЗАТЬ", fontWeight = FontWeight.Bold, color = Color.Black) }
    }
}

// --- КОМПОНЕНТ: ШТОРКА ДОБАВЛЕНИЯ/РЕДАКТИРОВАНИЯ (СЖАТАЯ ВЫСОТА + PEEKING) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseSheet(categories: List<String>, uniquePlaces: List<String>, initialExpense: Expense?, initialPlanned: PlannedExpense?, 
                    onSaveExpense: (Expense) -> Unit, onSavePlanned: (PlannedExpense) -> Unit, 
                    onDeleteExpense: (String) -> Unit, onDeletePlanned: (String) -> Unit) {
    val isEdit = initialExpense != null || initialPlanned != null
    var isPlanned by remember { mutableStateOf(initialPlanned != null) }
    var amountInput by remember { mutableStateOf(initialExpense?.amount?.toString()?.removeSuffix(".0") ?: initialPlanned?.amount?.toString()?.removeSuffix(".0") ?: "") }
    var placeInput by remember { mutableStateOf(initialExpense?.place ?: initialPlanned?.name ?: "") }
    var selectedCategory by remember { mutableStateOf(initialExpense?.category ?: categories.firstOrNull() ?: "📦 Иное") }
    var selectedDate by remember { mutableStateOf(initialExpense?.date ?: LocalDate.now()) }
    var showSuggestions by remember { mutableStateOf(false) }

    val tfColors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = Color(0xFF2C2C2E), unfocusedContainerColor = Color(0xFF2C2C2E), focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.Transparent, cursorColor = MaterialTheme.colorScheme.primary)
    val context = LocalContext.current
    val datePickerDialog = DatePickerDialog(context, { _, y, m, d -> selectedDate = LocalDate.of(y, m + 1, d) }, selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)
    
    val filteredPlaces = uniquePlaces.filter { it.contains(placeInput, ignoreCase = true) && it != placeInput }.take(3)
    val scrollState = androidx.compose.foundation.rememberScrollState()

    Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 8.dp)) {
        
        // --- 1. ЗАКРЕПЛЕННАЯ ШАПКА ---
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (isEdit) "Редактирование" else (if (isPlanned) "Отложенная трата" else "Новый расход"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
            if (!isEdit) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Отложить", fontSize = 12.sp, color = Color.Gray)
                    Switch(checked = isPlanned, onCheckedChange = { isPlanned = it }, modifier = Modifier.padding(start = 8.dp).scale(0.8f), colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = amountInput, onValueChange = { amountInput = it }, label = { Text("Сумма (₽)", color = Color.Gray) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = tfColors)
        
        OutlinedTextField(
            value = placeInput, 
            onValueChange = { placeInput = it; showSuggestions = it.isNotBlank() }, 
            label = { Text(if (isPlanned) "Название (Например: КАСКО)" else "Место или комментарий", color = Color.Gray) }, 
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).onFocusChanged { showSuggestions = it.isFocused && placeInput.isNotBlank() }, 
            shape = RoundedCornerShape(12.dp), colors = tfColors
        )
        AnimatedVisibility(visible = showSuggestions && filteredPlaces.isNotEmpty() && !isPlanned) {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
                Column {
                    filteredPlaces.forEach { place ->
                        Text(place, color = Color.White, modifier = Modifier.fillMaxWidth().clickable { placeInput = place; showSuggestions = false }.padding(12.dp))
                    }
                }
            }
        }
        
        if (!isPlanned) {
            Text("Категория", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            
            // --- 2. СКРОЛЛИРУЕМАЯ ЗОНА (ОГРАНИЧЕНИЕ ВЫСОТЫ) ---
            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 210.dp)) {
                Column(modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(bottom = 32.dp)
                ) {
                    val columns = 3
                    categories.chunked(columns).forEach { rowCats ->
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowCats.forEach { cat ->
                                val selected = selectedCategory == cat
                                Box(modifier = Modifier.weight(1f).height(52.dp).clickable { selectedCategory = cat }.background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color(0xFF2C2C2E), RoundedCornerShape(12.dp)).border(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp)).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                                    Text(cat, color = if (selected) MaterialTheme.colorScheme.primary else Color.White, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }
                            repeat(columns - rowCats.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                
                Box(modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(brush = Brush.verticalGradient(colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surface)))
                )
            }
            
            // --- 3. ЗАКРЕПЛЕННЫЙ ПОДВАЛ ---
            OutlinedButton(onClick = { datePickerDialog.show() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(50.dp), shape = RoundedCornerShape(12.dp), border = null, colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF2C2C2E))) { 
                Text("Дата: ${selectedDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}", color = Color.White) 
            }
        }
        
        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isEdit) {
                Button(
                    onClick = { if (isPlanned) onDeletePlanned(initialPlanned!!.id) else onDeleteExpense(initialExpense!!.id) }, 
                    modifier = Modifier.size(50.dp), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color.White) }
            }
            Button(
                onClick = {
                    val amt = amountInput.replace(",", ".").replace(" ", "").toDoubleOrNull() ?: 0.0
                    if (amt > 0) {
                        val id = initialExpense?.id ?: initialPlanned?.id ?: java.util.UUID.randomUUID().toString()
                        if (isPlanned) onSavePlanned(PlannedExpense(id, initialPlanned?.monthStr ?: java.time.YearMonth.now().toString(), placeInput, amt))
                        else onSaveExpense(Expense(id, amt, placeInput, selectedCategory, selectedDate))
                    }
                },
                modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("СОХРАНИТЬ", fontWeight = FontWeight.Bold, color = Color.Black) }
        }
    }
}

// --- ВКЛАДКА 2: АНАЛИТИКА (ЦВЕТНЫЕ ПРОГРЕСС-БАРЫ) ---
@Composable
fun AnalyticsScreen(expenses: List<Expense>) {
    var monthOffset by remember { mutableStateOf(0) }
    var swiped by remember { mutableStateOf(false) }
    
    val displayMonth = YearMonth.now().plusMonths(monthOffset.toLong())
    val monthExpenses = expenses.filter { YearMonth.from(it.date) == displayMonth }
    val totalSpent = monthExpenses.sumOf { it.amount }
    val grouped = monthExpenses.groupBy { it.category }.mapValues { it.value.sumOf { e -> e.amount } }.toList().sortedByDescending { it.second }

    val chartColors = if (grouped.size == 2) {
        listOf(Color(0xFFFFDD2D), Color(0xFF32D74B))
    } else {
        listOf(Color(0xFFFFDD2D), Color(0xFF0A84FF), Color(0xFF32D74B), Color(0xFFFF9F0A), Color(0xFFBF5AF2), Color(0xFFFF453A), Color(0xFF64D2FF), Color(0xFF8E8E93))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp).pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { swiped = false }, onDragEnd = { swiped = false }, onDragCancel = { swiped = false },
                onHorizontalDrag = { change, dragAmount -> 
                    change.consume()
                    if (!swiped) {
                        if (dragAmount > 50) { monthOffset -= 1; swiped = true }
                        else if (dragAmount < -50) { monthOffset += 1; swiped = true }
                    }
                }
            )
        }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { monthOffset -= 1 }) { Icon(Icons.Default.KeyboardArrowLeft, "Назад", tint = Color.Gray) }
            Text("Сводка за $displayMonth", style = MaterialTheme.typography.titleMedium, color = Color.White)
            IconButton(onClick = { monthOffset += 1 }) { Icon(Icons.Default.KeyboardArrowRight, "Вперед", tint = Color.Gray) }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Box(modifier = Modifier.fillMaxWidth().height(220.dp).padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                if (totalSpent > 0) {
                    Canvas(modifier = Modifier.size(160.dp)) {
                        var startAngle = -90f
                        grouped.forEachIndexed { index, pair ->
                            val sweepAngle = (pair.second / totalSpent).toFloat() * 360f
                            drawArc(color = chartColors[index % chartColors.size], startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = 40f))
                            startAngle += sweepAngle
                        }
                    }
                } else {
                    Canvas(modifier = Modifier.size(160.dp)) { drawArc(color = Color(0xFF333333), startAngle = -90f, sweepAngle = 360f, useCenter = false, style = Stroke(width = 40f)) }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Потрачено", fontSize = 12.sp, color = Color.Gray)
                    Text(formatMoneyWhole(totalSpent), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            LazyColumn {
                items(grouped.size) { index ->
                    val pair = grouped[index]
                    val percent = if (totalSpent > 0) ((pair.second / totalSpent) * 100).roundToInt() else 0
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(chartColors[index % chartColors.size]))
                                Text(pair.first, color = Color.White, modifier = Modifier.padding(start = 12.dp))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(formatMoneyFull(pair.second), color = Color.White, fontWeight = FontWeight.Bold)
                                Text("$percent%", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        LinearProgressIndicator(
                            progress = if (totalSpent > 0) (pair.second / totalSpent).toFloat() else 0f,
                            color = chartColors[index % chartColors.size],
                            trackColor = Color(0xFF333333),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
    }
}

// --- ВКЛАДКА 3: ИСТОРИЯ ---
@Composable
fun HistoryScreen(expenses: List<Expense>, filters: Set<String>, searchQuery: String, onOpenFilter: () -> Unit, onEdit: (Expense) -> Unit) {
    var monthOffset by remember { mutableStateOf(0) }
    var swiped by remember { mutableStateOf(false) }
    
    val displayMonth = YearMonth.now().plusMonths(monthOffset.toLong())
    val monthExpenses = expenses.filter { 
        YearMonth.from(it.date) == displayMonth && 
        (filters.isEmpty() || filters.contains(it.category)) &&
        (searchQuery.isBlank() || it.place.contains(searchQuery, ignoreCase = true))
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp).pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { swiped = false }, onDragEnd = { swiped = false }, onDragCancel = { swiped = false },
                onHorizontalDrag = { change, dragAmount -> 
                    change.consume()
                    if (!swiped) {
                        if (dragAmount > 50) { monthOffset -= 1; swiped = true }
                        else if (dragAmount < -50) { monthOffset += 1; swiped = true }
                    }
                }
            )
        }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { monthOffset -= 1 }) { Icon(Icons.Default.KeyboardArrowLeft, "Назад", tint = Color.Gray) }
            Text("История за $displayMonth", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.clickable { onOpenFilter() }.padding(8.dp)) {
                    Icon(Icons.Default.Menu, "Фильтр", tint = if (filters.isNotEmpty() || searchQuery.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray)
                    if (filters.isNotEmpty() || searchQuery.isNotBlank()) { Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).align(Alignment.TopEnd)) }
                }
                IconButton(onClick = { monthOffset += 1 }) { Icon(Icons.Default.KeyboardArrowRight, "Вперед", tint = Color.Gray) }
            }
        }

        LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
            items(monthExpenses.sortedByDescending { it.date }) { exp ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onEdit(exp) }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(exp.category, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("${exp.place} • ${exp.date.format(DateTimeFormatter.ofPattern("dd.MM"))}", fontSize = 12.sp, color = Color.Gray)
                        }
                        Text(formatMoneyFull(exp.amount), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
}

// --- ВКЛАДКА 4: НАСТРОЙКИ (АККОРДЕОНЫ И СОРТИРОВКА) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(budgets: Map<YearMonth, Double>, categories: List<String>, onUpdateBudgets: (Map<YearMonth, Double>) -> Unit, onUpdateCategories: (String?, String?, List<String>) -> Unit, onImportSuccess: () -> Unit) {
    var newLimit by remember { mutableStateOf("") }
    var selectedMonth by remember { mutableStateOf(YearMonth.now().monthValue) }
    var selectedYear by remember { mutableStateOf(YearMonth.now().year) }
    
    var catToEdit by remember { mutableStateOf<String?>(null) }
    var isAddingCat by remember { mutableStateOf(false) }
    
    var categoriesExpanded by remember { mutableStateOf(false) }
    var budgetsExpanded by remember { mutableStateOf(false) }

    val tfColors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = Color(0xFF2C2C2E), unfocusedContainerColor = Color(0xFF2C2C2E), focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.Transparent)
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(Storage.exportAll(context).toByteArray()) }
                Toast.makeText(context, "Резервная копия сохранена!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(context, "Ошибка сохранения", Toast.LENGTH_SHORT).show() }
        }
    }
    
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { BufferedReader(InputStreamReader(it)).readText() }
                if (json != null && Storage.importAll(context, json)) {
                    Toast.makeText(context, "Данные успешно восстановлены!", Toast.LENGTH_SHORT).show()
                    onImportSuccess()
                } else { Toast.makeText(context, "Ошибка формата файла", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) { Toast.makeText(context, "Ошибка чтения файла", Toast.LENGTH_SHORT).show() }
        }
    }

    LazyColumn(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        item { Text("Настройки", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(bottom = 16.dp)) }
        
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Установить новый лимит", fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(bottom = 12.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(value = selectedMonth.toString(), onValueChange = { selectedMonth = it.toIntOrNull() ?: 1 }, label = { Text("Месяц", color = Color.Gray) }, modifier = Modifier.weight(1f).padding(end = 4.dp), shape = RoundedCornerShape(12.dp), colors = tfColors)
                        OutlinedTextField(value = selectedYear.toString(), onValueChange = { selectedYear = it.toIntOrNull() ?: 2026 }, label = { Text("Год", color = Color.Gray) }, modifier = Modifier.weight(1f).padding(start = 4.dp), shape = RoundedCornerShape(12.dp), colors = tfColors)
                    }
                    OutlinedTextField(value = newLimit, onValueChange = { newLimit = it }, label = { Text("Сумма (₽)", color = Color.Gray) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(12.dp), colors = tfColors)
                    Button(onClick = {
                        val limitD = newLimit.replace(",", ".").replace(" ", "").toDoubleOrNull()
                        if (limitD != null && selectedMonth in 1..12) {
                            val newB = budgets.toMutableMap()
                            newB[YearMonth.of(selectedYear, selectedMonth)] = limitD
                            onUpdateBudgets(newB); newLimit = ""
                        }
                    }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(50.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("СОХРАНИТЬ", fontWeight = FontWeight.Bold, color = Color.Black) }
                }
            }
        }
        
        item {
            Row(modifier = Modifier.fillMaxWidth().clickable { categoriesExpanded = !categoriesExpanded }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Управление категориями", fontWeight = FontWeight.Bold, color = Color.Gray)
                Icon(if (categoriesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.Gray)
            }
        }
        if (categoriesExpanded) {
            item {
                Text("+ Добавить категорию", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().clickable { isAddingCat = true }.padding(vertical = 8.dp))
            }
            items(categories.size) { index ->
                val cat = categories[index]
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(cat, color = Color.White, modifier = Modifier.weight(1f).clickable { catToEdit = cat })
                        Row {
                            if (index > 0) {
                                IconButton(onClick = { 
                                    val newList = categories.toMutableList()
                                    val temp = newList[index]; newList[index] = newList[index - 1]; newList[index - 1] = temp
                                    onUpdateCategories(null, null, newList)
                                }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.KeyboardArrowUp, "Вверх", tint = Color.Gray) }
                            }
                            if (index < categories.size - 1) {
                                IconButton(onClick = { 
                                    val newList = categories.toMutableList()
                                    val temp = newList[index]; newList[index] = newList[index + 1]; newList[index + 1] = temp
                                    onUpdateCategories(null, null, newList)
                                }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.KeyboardArrowDown, "Вниз", tint = Color.Gray) }
                            }
                        }
                    }
                }
            }
        }

        item { Text("Резервное копирование", fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp, top = 24.dp)) }
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Создайте копию данных (бэкап), чтобы не потерять историю расходов.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { exportLauncher.launch("BudgetBackup_${LocalDate.now()}.json") }, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E))) {
                            Text("Экспорт", color = Color.White)
                        }
                        Button(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E))) {
                            Text("Импорт", color = Color.White)
                        }
                    }
                }
            }
        }
        
        item {
            Row(modifier = Modifier.fillMaxWidth().clickable { budgetsExpanded = !budgetsExpanded }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("История лимитов", fontWeight = FontWeight.Bold, color = Color.Gray)
                Icon(if (budgetsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.Gray)
            }
        }
        if (budgetsExpanded) {
            items(budgets.toList().sortedByDescending { it.first }) { (ym, limit) ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(ym.toString(), fontWeight = FontWeight.Bold, color = Color.White)
                        Text(formatMoneyWhole(limit), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(100.dp)) }
    }

    if (catToEdit != null || isAddingCat) {
        var catName by remember { mutableStateOf(catToEdit ?: "") }
        AlertDialog(
            onDismissRequest = { catToEdit = null; isAddingCat = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(if (isAddingCat) "Новая категория" else "Изменить категорию", color = Color.White) },
            text = { 
                OutlinedTextField(
                    value = catName, 
                    onValueChange = { catName = it }, 
                    label = { Text("Эмодзи и название", color = Color.Gray) },
                    supportingText = { Text("Желательно до 12 символов", color = Color.Gray) },
                    colors = tfColors
                ) 
            },
            confirmButton = {
                Button(onClick = {
                    if (catName.isNotBlank()) {
                        val newList = if (isAddingCat) categories + catName else categories.map { if (it == catToEdit) catName else it }
                        onUpdateCategories(catToEdit, catName, newList)
                    }
                    catToEdit = null; isAddingCat = false
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Сохранить", color = Color.Black) }
            },
            dismissButton = {
                if (!isAddingCat) {
                    Button(onClick = {
                        val newList = categories.filter { it != catToEdit }
                        onUpdateCategories(catToEdit, null, newList)
                        catToEdit = null
                    }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Удалить", color = Color.White) }
                }
            }
        )
    }
}
