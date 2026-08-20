package com.rohansingh.focusforge

import android.app.Application
import com.rohansingh.focusforge.data.database.AppDatabase
import com.rohansingh.focusforge.data.repository.ExchangeConfigRepository
import com.rohansingh.focusforge.data.repository.FocusSessionRepository
import com.rohansingh.focusforge.data.repository.GoalRepository
import com.rohansingh.focusforge.data.repository.RestrictedAppRepository
import com.rohansingh.focusforge.data.repository.RewardRepository
import com.rohansingh.focusforge.data.repository.StatisticsRepository
import com.rohansingh.focusforge.data.repository.WalletRepository
import com.rohansingh.focusforge.domain.managers.BarterManager
import com.rohansingh.focusforge.domain.managers.FocusSessionManager
import com.rohansingh.focusforge.domain.managers.GoalManager
import com.rohansingh.focusforge.domain.managers.RewardManager
import com.rohansingh.focusforge.services.alarm.AndroidFocusSessionAlarmScheduler
import com.rohansingh.focusforge.services.daily.DailyGrantScheduler
import com.rohansingh.focusforge.services.notifications.FocusForgeNotificationManager

/**
 * Application class for FocusForge.
 * Initializes daily grant automation and core application dependencies.
 */
class FocusForgeApplication : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var walletRepository: WalletRepository
        private set
    lateinit var exchangeConfigRepository: ExchangeConfigRepository
        private set
    lateinit var goalRepository: GoalRepository
        private set
    lateinit var goalManager: GoalManager
        private set
    lateinit var rewardRepository: RewardRepository
        private set
    lateinit var rewardManager: RewardManager
        private set
    lateinit var barterManager: BarterManager
        private set
    lateinit var restrictedAppRepository: RestrictedAppRepository
        private set
    lateinit var focusSessionRepository: FocusSessionRepository
        private set
    lateinit var focusSessionManager: FocusSessionManager
        private set
    lateinit var statisticsRepository: StatisticsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        FocusForgeNotificationManager.createNotificationChannels(this)

        database = AppDatabase.getDatabase(this)
        walletRepository = WalletRepository(database.walletDao())
        exchangeConfigRepository = ExchangeConfigRepository(this)
        goalRepository = GoalRepository(
            database = database,
            goalTemplateDao = database.goalTemplateDao(),
            goalLogDao = database.goalLogDao(),
            goalStreakDao = database.goalStreakDao(),
            xpLogDao = database.xpLogDao(),
            walletDao = database.walletDao(),
            context = this
        )
        goalManager = GoalManager(goalRepository)
        rewardRepository = RewardRepository(database.rewardTemplateDao(), database.redemptionLogDao())
        rewardManager = RewardManager(rewardRepository, walletRepository, exchangeConfigRepository)
        barterManager = BarterManager(walletRepository, exchangeConfigRepository)
        restrictedAppRepository = RestrictedAppRepository(database.restrictedAppDao())
        focusSessionRepository = FocusSessionRepository(database.focusSessionDao())
        statisticsRepository = StatisticsRepository(
            goalLogDao = database.goalLogDao(),
            redemptionLogDao = database.redemptionLogDao(),
            xpLogDao = database.xpLogDao(),
            goalStreakDao = database.goalStreakDao(),
            focusSessionDao = database.focusSessionDao(),
            screenTimeLogDao = database.screenTimeLogDao(),
            walletDao = database.walletDao()
        )

        val alarmScheduler = AndroidFocusSessionAlarmScheduler(this)
        focusSessionManager = FocusSessionManager(
            focusSessionRepository = focusSessionRepository,
            goalManager = goalManager,
            alarmScheduler = alarmScheduler
        )


        // Ensure unique daily WorkManager automation is scheduled
        DailyGrantScheduler.scheduleDailyGrant(this)
    }

    companion object {
        lateinit var instance: FocusForgeApplication
            private set
    }
}
