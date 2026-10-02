package com.composables.jetpackcomposetemplate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Модели данных
data class PeriodLimit(val id: Int, val name: String, var limit: Int, var spent: Int = 0) {
    val remaining get() = limit - spent
}
data class Expense(val amount: Int, val place: String, val category: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                BudgetTrackerApp()
            }
        }
    }
}

@Composable
fun BudgetTrackerApp() {
    // Инициализация периодов
    var periods by remember { mutableStateOf(listOf(
        PeriodLimit(1, "01.10 - 04.10", 10390),
        PeriodLimit(2, "05.10 - 11.10", 18170),
        PeriodLimit(3, "12.10 - 18.10", 18170),
        PeriodLimit(4, "19.10 - 25.10", 18170),
        PeriodLimit(5, "26.10 - 31.10", 15580)
    )) }

    var expenses by remember { mutableStateOf(listOf<Expense>()) }

    // Поля ввода
    var amountInput by remember { mutableStateOf("") }
    var placeInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Продукты") }
    val categories = listOf("Продукты", "Транспорт", "Кафе", "Услуги", "Здоровье", "Развлечения")

Scaffold(
    topBar = { TopAppBar(title = { Text("Осталось трат на неделю") }) }
) { padding ->
    Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
        
        // Виджет остатков по периодам
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), elevation = 4.dp) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Текущие лимиты:")
                Spacer(modifier = Modifier.height(8.dp))
                periods.forEach { period ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), 
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(period.name)
                        Text("${period.remaining} ₽")
                    }
                }
            }
        }

        // Форма добавления расхода
        Text("Добавить расход")
        OutlinedTextField(
            value = amountInput,
            onValueChange = { amountInput = it },
            label = { Text("Сумма (₽)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = placeInput,
            onValueChange = { placeInput = it },
            label = { Text("Где потратил (место)") },
            modifier = Modifier.fillMaxWidth()
        )
        
        // Выбор категории (простой Dropdown для совместимости)
        var expanded by remember { mutableStateOf(false) }
        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Button(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Категория: $selectedCategory")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                categories.forEach { category ->
                    DropdownMenuItem(onClick = {
                        selectedCategory = category
                        expanded = false
                    }) {
                        Text(category)
                    }
                }
            }
        }
        
        Button(
            onClick = {
                val amount = amountInput.toIntOrNull() ?: 0
                if (amount > 0) {
                    periods = periods.map { 
                        if (it.id == 2) it.copy(spent = it.spent + amount) else it 
                    }
                    expenses = expenses + Expense(amount, placeInput, selectedCategory)
                    amountInput = ""
                    placeInput = ""
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text("Записать расход")
        }
    }
}
}
