package com.srtgo.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.srtgo.app.core.model.Passenger
import com.srtgo.app.core.model.PassengerType
import com.srtgo.app.core.model.RailType
import com.srtgo.app.core.model.Reservation
import com.srtgo.app.core.model.SeatType
import com.srtgo.app.domain.usecase.MacroConfig
import com.srtgo.app.domain.usecase.MacroStatus
import com.srtgo.app.domain.usecase.RunMacroUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class ServiceMacroState(
    val isRunning: Boolean = false,
    val attempts: Int = 0,
    val elapsedSeconds: Long = 0,
    val status: String = "idle",
    val reservation: Reservation? = null,
    val lastError: String? = null
)

@AndroidEntryPoint
class MacroForegroundService : Service() {

    @Inject lateinit var runMacroUseCase: RunMacroUseCase

    private lateinit var notificationManager: MacroNotificationManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var macroJob: Job? = null

    companion object {
        const val ACTION_START = "START_MACRO"
        const val ACTION_CANCEL = "CANCEL_MACRO"
        const val EXTRA_CONFIG_JSON = "config_json"

        private val _macroState = MutableStateFlow(ServiceMacroState())
        val macroState: StateFlow<ServiceMacroState> = _macroState.asStateFlow()

        fun startMacro(context: Context, configJson: String) {
            val intent = Intent(context, MacroForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CONFIG_JSON, configJson)
            }
            context.startForegroundService(intent)
        }

        fun cancelMacro(context: Context) {
            val intent = Intent(context, MacroForegroundService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = MacroNotificationManager(this)
        notificationManager.createChannel()
        startForeground(
            MacroNotificationManager.NOTIFICATION_ID,
            notificationManager.buildProgressNotification(0, 0)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val configJson = intent.getStringExtra(EXTRA_CONFIG_JSON) ?: "{}"
                startMacroExecution(configJson)
            }
            ACTION_CANCEL -> {
                cancelMacroExecution()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        macroJob?.cancel()
        serviceScope.cancel()
        _macroState.value = ServiceMacroState()
    }

    private fun startMacroExecution(configJson: String) {
        macroJob?.cancel()
        _macroState.value = ServiceMacroState(isRunning = true, status = "searching")

        val config = parseMacroConfig(configJson) ?: run {
            _macroState.value = ServiceMacroState(
                isRunning = false,
                status = "failed",
                lastError = "잘못된 매크로 설정"
            )
            stopSelf()
            return
        }

        macroJob = serviceScope.launch {
            runMacroUseCase(config).collect { state ->
                val elapsedSec = state.elapsedMs / 1000

                _macroState.value = ServiceMacroState(
                    isRunning = state.status == MacroStatus.SEARCHING,
                    attempts = state.attempts,
                    elapsedSeconds = elapsedSec,
                    status = state.status.name.lowercase(),
                    reservation = state.reservation,
                    lastError = state.lastError
                )

                when (state.status) {
                    MacroStatus.SEARCHING -> {
                        val notification = notificationManager.buildProgressNotification(
                            state.attempts, elapsedSec
                        )
                        notificationManager.let {
                            (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager)
                                .notify(MacroNotificationManager.NOTIFICATION_ID, notification)
                        }
                    }
                    MacroStatus.SUCCESS -> {
                        val info = state.reservation?.displayString ?: "예매 완료"
                        val notification = notificationManager.buildSuccessNotification(info)
                        (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager)
                            .notify(MacroNotificationManager.NOTIFICATION_ID, notification)
                        stopSelf()
                    }
                    MacroStatus.FAILED -> {
                        val error = state.lastError ?: "알 수 없는 오류"
                        val notification = notificationManager.buildFailureNotification(error)
                        (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager)
                            .notify(MacroNotificationManager.NOTIFICATION_ID, notification)
                        stopSelf()
                    }
                    MacroStatus.CANCELLED -> {
                        stopSelf()
                    }
                }
            }
        }
    }

    private fun cancelMacroExecution() {
        macroJob?.cancel()
        _macroState.value = ServiceMacroState(
            isRunning = false,
            status = "cancelled"
        )
        stopSelf()
    }

    private fun parseMacroConfig(json: String): MacroConfig? {
        return try {
            val obj = JSONObject(json)
            val railType = RailType.valueOf(obj.optString("railType", "SRT"))
            val passengers = buildList {
                val adult = obj.optInt("adultCount", 1)
                val child = obj.optInt("childCount", 0)
                val senior = obj.optInt("seniorCount", 0)
                val disability = obj.optInt("disabilityCount", 0)
                if (adult > 0) add(Passenger(PassengerType.ADULT, adult))
                if (child > 0) add(Passenger(PassengerType.CHILD, child))
                if (senior > 0) add(Passenger(PassengerType.SENIOR, senior))
                if (disability > 0) add(Passenger(PassengerType.DISABILITY_1_3, disability))
            }
            val selectedTrains = buildList {
                val arr = obj.optJSONArray("selectedTrains") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    add(arr.getInt(i))
                }
            }

            MacroConfig(
                railType = railType,
                departure = obj.optString("departureCode"),
                arrival = obj.optString("arrivalCode"),
                date = obj.optString("date"),
                time = obj.optString("time"),
                passengers = passengers,
                seatType = SeatType.fromCode(obj.optInt("seatType", 1)),
                selectedTrainIndices = selectedTrains,
                autoPay = obj.optBoolean("autoPay", false)
            )
        } catch (_: Exception) {
            null
        }
    }
}
