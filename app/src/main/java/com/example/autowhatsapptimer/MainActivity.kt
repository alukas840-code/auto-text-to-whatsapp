package com.example.autowhatsapptimer

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Entity(tableName = "scheduled_messages")
data class ScheduledMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupsCsv: String,
    val message: String,
    val timeIso: String,
    val enabled: Boolean = true
)

@Dao
interface ScheduledMessageDao {
    @Query("SELECT * FROM scheduled_messages ORDER BY timeIso ASC")
    suspend fun getAll(): List<ScheduledMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ScheduledMessage): Long

    @Query("UPDATE scheduled_messages SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM scheduled_messages WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [ScheduledMessage::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): ScheduledMessageDao
}

class SchedulerViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "scheduler.db"
    ).build()

    private val dao = db.dao()
    private val _items = MutableStateFlow<List<ScheduledMessage>>(emptyList())
    val items: StateFlow<List<ScheduledMessage>> = _items.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _items.value = dao.getAll()
        }
    }

    fun add(groupsCsv: String, message: String, localDateTime: LocalDateTime) {
        viewModelScope.launch {
            val item = ScheduledMessage(
                groupsCsv = groupsCsv,
                message = message,
                timeIso = localDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )
            val id = dao.insert(item)
            scheduleWork(getApplication(), item.copy(id = id))
            refresh()
        }
    }

    fun toggle(item: ScheduledMessage, enabled: Boolean) {
        viewModelScope.launch {
            dao.setEnabled(item.id, enabled)
            if (enabled) {
                scheduleWork(getApplication(), item.copy(enabled = true))
            } else {
                WorkManager.getInstance(getApplication())
                    .cancelUniqueWork("msg_${item.id}")
            }
            refresh()
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val vm: SchedulerViewModel = viewModel(
                factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            )
            MaterialTheme {
                SchedulerScreen(vm)
            }
        }
    }
}

@Composable
fun SchedulerScreen(vm: SchedulerViewModel) {
    val items by vm.items.collectAsStateCompat()

    var groups by remember { mutableStateOf("Group1,Group2") }
    var message by remember { mutableStateOf("Доброе утро!") }
    var dateTimeRaw by remember { mutableStateOf("2026-04-04T09:00") }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Планировщик сообщений WhatsApp", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Формат даты/времени: yyyy-MM-ddTHH:mm (локальное время телефона). " +
                    "Отправка выполняется через deep-link в WhatsApp для каждой группы."
            )

            OutlinedTextField(value = groups, onValueChange = { groups = it }, label = { Text("Группы (через запятую)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text("Текст сообщения") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = dateTimeRaw, onValueChange = { dateTimeRaw = it }, label = { Text("Дата и время") }, modifier = Modifier.fillMaxWidth())

            Button(onClick = {
                runCatching {
                    val dt = LocalDateTime.parse(dateTimeRaw, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
                    vm.add(groups, message, dt)
                }
            }) {
                Text("Добавить расписание")
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items) { item ->
                    MessageCard(item = item, onToggle = { enabled -> vm.toggle(item, enabled) })
                }
            }
        }
    }
}

@Composable
fun MessageCard(item: ScheduledMessage, onToggle: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Группы: ${item.groupsCsv}")
                Text("Сообщение: ${item.message}")
                Text("Когда: ${item.timeIso}")
            }
            Switch(checked = item.enabled, onCheckedChange = onToggle)
        }
    }
}

class MessageDispatchWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val groupsCsv = inputData.getString("groups") ?: return Result.failure()
        val message = inputData.getString("message") ?: return Result.failure()

        val groups = groupsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
        groups.forEach { group ->
            val encodedText = Uri.encode("[$group] $message")
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://wa.me/?text=$encodedText")
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            applicationContext.startActivity(intent)
        }
        return Result.success()
    }
}

private fun scheduleWork(context: Context, item: ScheduledMessage) {
    val at = LocalDateTime.parse(item.timeIso, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    val now = System.currentTimeMillis()
    val delay = (at - now).coerceAtLeast(0)

    val request = OneTimeWorkRequestBuilder<MessageDispatchWorker>()
        .setInitialDelay(Duration.ofMillis(delay))
        .setInputData(
            workDataOf(
                "groups" to item.groupsCsv,
                "message" to item.message
            )
        )
        .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
        "msg_${item.id}",
        ExistingWorkPolicy.REPLACE,
        request
    )
}

@Composable
private fun <T> StateFlow<T>.collectAsStateCompat(): androidx.compose.runtime.State<T> {
    return this.collectAsState(initial = value)
}
