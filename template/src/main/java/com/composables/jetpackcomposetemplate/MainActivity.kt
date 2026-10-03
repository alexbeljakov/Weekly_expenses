package com.composables.jetpackcomposetemplate

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToInt

data class Expense(
    val id: String = UUID.randomUUID().toString(),
    val amount: Double,
    val place: String,
    val category: String,
    val date: LocalDate = LocalDate.now()
)

data class PlannedExpense(
    val id: String = UUID.randomUUID().toString(),
    val monthStr: String, // "YYYY-MM"
    val name: String,
    val amount: Double
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFFD600),
                    surface = Color(0xFF1C1C1E),
                    background = Color(0xFF121212),
                    error = Color(0xFFFF453A)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BudgetApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetApp() {
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var currentTab by remember { mutableIntStateOf(0) } // 0: Главная, 1: Сводка, 2: История, 3: Настройки
    
    // Стейты данных
    var expenses by remember { mutableStateOf(listOf(
        Expense(amount = 2052.26, place = "Магнит", category = "🛒 Продукты", date = LocalDate.now().minusDays(1)),
        Expense(amount = 120.0, place = "Ozon", category = "🛍️ Маркетплейсы", date = LocalDate.now())
    )) }
    
    var planned by remember { mutableStateOf(listOf<PlannedExpense>()) }
    
    var categories by remember { mutableStateOf(listOf(
        "🛒 Продукты", "🛍️ Маркетплейсы", "🚗 Транспорт", 
        "🍔 Кафе", "✂️ Услуги", "💊 Здоровье", 
        "🍿 Развлечения", "🏠 Дом", "📦 Иное"
    )) }

    // Управление шторками
    var showAddSheet by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var expenseToEdit by remember { mutableStateOf<Expense?>(null) }
    var plannedToEdit by remember { mutableStateOf<PlannedExpense?>(null) }
    var selectedCategoryDetail by remember { mutableStateOf<String?>(null) } // Для шторки детализации

    // Фильтры истории
    var historyFilters by remember { mutableStateOf(setOf<String>()) }
    var historySearchQuery by remember { mutableStateOf("") }
    
    // Перенос остатка
    var carryOverEnabled by remember { mutableStateOf(false) }

    val monthStr = currentMonth.toString()
    val uniquePlaces = expenses.map { it.place }.filter { it.isNotBlank() }.distinct()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF1C1C1E)) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("Главная") },
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary, unselectedIconColor = Color.Gray)
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.PieChart, contentDescription = null) },
                        label = { Text("Сводка") },
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary, unselectedIconColor = Color.Gray)
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.List, contentDescription = null) },
                        label = { Text("История") },
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary, unselectedIconColor = Color.Gray)
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Настройки") },
                        selected = currentTab == 3,
                        onClick = { currentTab = 3 },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary, unselectedIconColor = Color.Gray)
                    )
                }
            },
            floatingActionButton = {
                if (currentTab != 3) {
                    FloatingActionButton(
                        onClick = { 
                            expenseToEdit = null
                            plannedToEdit = null
                            showAddSheet = true 
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.Black,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить расход")
                    }
                }
            },
            containerColor = Color(0xFF121212)
        ) { paddingValues ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
                // Выбор месяца (шапка)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Назад", tint = Color.White)
                    }
                    Text(currentMonth.toString(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Вперед", tint = Color.White)
                    }
                }

                when (currentTab) {
                    0 -> MainTabContent(expenses, planned, currentMonth)
                    1 -> SummaryTabContent(expenses, currentMonth, onCategoryClick = { cat -> selectedCategoryDetail = cat })
                    2 -> HistoryTabContent(expenses, currentMonth, historyFilters, historySearchQuery, onFilterClick = { showFilterSheet = true }, onEditExpense = { exp -> expenseToEdit = exp; showAddSheet = true })
                    3 -> SettingsTabContent(carryOverEnabled, { carryOverEnabled = it }, categories, { categories = it })
                }
            }
        }

        // --- УМНЫЕ ШТОРКИ ---
        AnimatedVisibility(visible = showAddSheet || showFilterSheet || selectedCategoryDetail != null, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { 
                showAddSheet = false
                showFilterSheet = false
                selectedCategoryDetail = null
            })
        }

        // 1. Шторка Добавления/Редактирования
        AnimatedVisibility(visible = showAddSheet, enter = slideInVertically(initialOffsetY = { it }), exit = slideOutVertically(targetOffsetY = { it }), modifier = Modifier.align(Alignment.BottomCenter)) {
            var offsetY by remember { mutableStateOf(0f) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, offsetY.roundToInt()) }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (offsetY > 200f) showAddSheet = false
                                offsetY = 0f
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                if (dragAmount > 0 || offsetY > 0) offsetY = (offsetY + dragAmount).coerceAtLeast(0f)
                            }
                        )
                    }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}, 
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), 
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
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

        // 2. Шторка Фильтрации
        AnimatedVisibility(visible = showFilterSheet, enter = slideInVertically(initialOffsetY = { it }), exit = slideOutVertically(targetOffsetY = { it }), modifier = Modifier.align(Alignment.BottomCenter)) {
            var offsetY by remember { mutableStateOf(0f) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, offsetY.roundToInt()) }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (offsetY > 200f) showFilterSheet = false
                                offsetY = 0f
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                if (dragAmount > 0 || offsetY > 0) offsetY = (offsetY + dragAmount).coerceAtLeast(0f)
                            }
                        )
                    }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}, 
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), 
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(Color.DarkGray))
                }
                FilterSheet(categories = categories, uniquePlaces = uniquePlaces, selected = historyFilters, searchQuery = historySearchQuery, onApply = { newFilters, newSearch -> historyFilters = newFilters; historySearchQuery = newSearch; showFilterSheet = false })
            }
        }

        // 3. Шторка Детализации категории по местам
        AnimatedVisibility(visible = selectedCategoryDetail != null, enter = slideInVertically(initialOffsetY = { it }), exit = slideOutVertically(targetOffsetY = { it }), modifier = Modifier.align(Alignment.BottomCenter)) {
            var offsetY by remember { mutableStateOf(0f) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, offsetY.roundToInt()) }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (offsetY > 200f) selectedCategoryDetail = null
                                offsetY = 0f
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                if (dragAmount > 0 || offsetY > 0) offsetY = (offsetY + dragAmount).coerceAtLeast(0f)
                            }
                        )
                    }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}, 
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), 
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(Color.DarkGray))
                }
                selectedCategoryDetail?.let { cat ->
                    CategoryDetailSheet(
                        category = cat,
                        expenses = expenses,
                        currentMonthStr = monthStr,
                        onClose = { selectedCategoryDetail = null }
                    )
                }
            }
        }
    }
}

// --- ВКЛАДКА: ГЛАВНАЯ ---
@Composable
fun MainTabContent(expenses: List<Expense>, planned: List<PlannedExpense>, currentMonth: YearMonth) {
    val monthStr = currentMonth.toString()
    val totalSpent = expenses.filter { it.date.toString().startsWith(monthStr) }.sumOf { it.amount }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Осталось на этой неделе", color = Color.Gray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("${(50000 - totalSpent).toInt()} ₽", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Потрачено за месяц: ${totalSpent.toInt()} ₽", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

// --- ВКЛАДКА: СВОДКА (АНАЛИТИКА) ---
@Composable
fun SummaryTabContent(expenses: List<Expense>, currentMonth: YearMonth, onCategoryClick: (String) -> Unit) {
    val monthStr = currentMonth.toString()
    val monthExpenses = expenses.filter { it.date.toString().startsWith(monthStr) }
    val totalMonth = monthExpenses.sumOf { it.amount }
    val grouped = monthExpenses.groupBy { it.category }.mapValues { entry -> entry.value.sumOf { it.amount } }.entries.sortedByDescending { it.value }
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
        Text("Сводка за $monthStr", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        
        if (grouped.isEmpty()) {
            Text("Нет данных за этот месяц", color = Color.Gray, modifier = Modifier.padding(vertical = 24.dp))
        } else {
            grouped.forEach { (cat, amount) ->
                val percent = if (totalMonth > 0) (amount / totalMonth * 100).toInt() else 0
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onCategoryClick(cat) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(cat, color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { percent / 100f },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color(0xFF2C2C2E)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${amount.toInt()} ₽", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("$percent%", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// --- ВКЛАДКА: ИСТОРИЯ ---
@Composable
fun HistoryTabContent(expenses: List<Expense>, currentMonth: YearMonth, filters: Set<String>, searchQuery: String, onFilterClick: () -> Unit, onEditExpense: (Expense) -> Unit) {
    val monthStr = currentMonth.toString()
    val filtered = expenses.filter { 
        it.date.toString().startsWith(monthStr) && 
        (filters.isEmpty() || filters.contains(it.category)) &&
        (searchQuery.isBlank() || it.place.contains(searchQuery, ignoreCase = true))
    }.sortedByDescending { it.date }
    
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("История за $monthStr", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
            IconButton(onClick = onFilterClick) {
                Icon(Icons.Default.FilterList, contentDescription = "Фильтр", tint = if (filters.isNotEmpty() || searchQuery.isNotBlank()) MaterialTheme.colorScheme.primary else Color.White)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
            if (filtered.isEmpty()) {
                Text("Ничего не найдено", color = Color.Gray, modifier = Modifier.padding(top = 24.dp))
            } else {
                filtered.forEach { exp ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onEditExpense(exp) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(exp.place.ifBlank { exp.category }, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${exp.category} • ${exp.date.format(DateTimeFormatter.ofPattern("dd.MM"))}", color = Color.Gray, fontSize = 12.sp)
                            }
                            Text("${exp.amount.toInt()} ₽", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

// --- ВКЛАДКА: НАСТРОЙКИ ---
@Composable
fun SettingsTabContent(carryOver: Boolean, onCarryOverChange: (Boolean) -> Unit, categories: List<String>, onCategoriesChange: (List<String>) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Настройки", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Перенос остатка месяца", color = Color.White)
            Switch(checked = carryOver, onCheckedChange = onCarryOverChange)
        }
    }
}

// --- ШТОРКА: ДОБАВЛЕНИЕ / РЕДАКТИРОВАНИЕ ---
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
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 8.dp)) {
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
            
            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 210.dp)) {
                Column(modifier = Modifier.verticalScroll(scrollState).padding(bottom = 32.dp)) {
                    val columns = 3
                    categories.chunked(columns).forEach { rowCats ->
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowCats.forEach { cat ->
                                val selected = selectedCategory == cat
                                Box(modifier = Modifier.weight(1f).height(52.dp).clickable { selectedCategory = cat }.background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color(0xFF2C2C2E), RoundedCornerShape(12.dp)).border(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp)).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                                    Text(cat, color = if (selected) MaterialTheme.colorScheme.primary else Color.White, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            repeat(columns - rowCats.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(32.dp).background(brush = Brush.verticalGradient(colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surface))))
            }
            
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
                        val id = initialExpense?.id ?: initialPlanned?.id ?: UUID.randomUUID().toString()
                        if (isPlanned) onSavePlanned(PlannedExpense(id, initialPlanned?.monthStr ?: YearMonth.now().toString(), placeInput, amt))
                        else onSaveExpense(Expense(id, amt, placeInput, selectedCategory, selectedDate))
                    }
                },
                modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("СОХРАНИТЬ", fontWeight = FontWeight.Bold, color = Color.Black) }
        }
    }
}

// --- ШТОРКА: ФИЛЬТР ---
@Composable
fun FilterSheet(categories: List<String>, uniquePlaces: List<String>, selected: Set<String>, searchQuery: String, onApply: (Set<String>, String) -> Unit) {
    var currentSelection by remember { mutableStateOf(selected) }
    var currentSearch by remember { mutableStateOf(searchQuery) }
    var showSuggestions by remember { mutableStateOf(false) }
    
    val filteredPlaces = uniquePlaces.filter { it.contains(currentSearch, ignoreCase = true) && it != currentSearch }.take(3)
    val tfColors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = Color(0xFF2C2C2E), unfocusedContainerColor = Color(0xFF2C2C2E), focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.Transparent, cursorColor = MaterialTheme.colorScheme.primary)
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 8.dp)) {
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
        Text("Категории", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
        
        Box(modifier = Modifier.fillMaxWidth().heightIn(max = 210.dp)) {
            Column(modifier = Modifier.verticalScroll(scrollState).padding(bottom = 32.dp)) {
                val columns = 3
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
            Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(32.dp).background(brush = Brush.verticalGradient(colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surface))))
        }
        
        Button(
            onClick = { onApply(currentSelection, currentSearch) },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) { Text("ПОКАЗАТЬ", fontWeight = FontWeight.Bold, color = Color.Black) }
    }
}

// --- ШТОРКА: ДЕТАЛИЗАЦИЯ КАТЕГОРИИ ПО МЕСТАМ ---
@Composable
fun CategoryDetailSheet(
    category: String,
    expenses: List<Expense>,
    currentMonthStr: String,
    onClose: () -> Unit
) {
    val categoryExpenses = expenses.filter { 
        it.category == category && it.date.toString().startsWith(currentMonthStr) 
    }
    
    val placeMap = categoryExpenses.groupBy { it.place.ifBlank { "Без названия" } }
        .mapValues { entry -> entry.value.sumOf { it.amount } }
        .entries.sortedByDescending { it.value }

    val totalCategoryAmount = placeMap.sumOf { it.value }
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(category, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Всего: ${totalCategoryAmount.toInt()} ₽", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text("Траты по местам", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))

        Box(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
            Column(modifier = Modifier.verticalScroll(scrollState).padding(bottom = 32.dp)) {
                if (placeMap.isEmpty()) {
                    Text("В этом месяце трат в категории нет", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    placeMap.forEach { (place, amount) ->
                        val percent = if (totalCategoryAmount > 0) (amount / totalCategoryAmount * 100).toInt() else 0
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(place, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { percent / 100f },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = Color(0xFF2C2C2E)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${amount.toInt()} ₽", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("$percent%", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(32.dp).background(brush = Brush.verticalGradient(colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surface))))
        }

        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E))
        ) { Text("ЗАКРЫТЬ", fontWeight = FontWeight.Bold, color = Color.White) }
    }
}


