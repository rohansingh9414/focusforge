package com.rohansingh.focusforge.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.rohansingh.focusforge.domain.models.ExchangeConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.exchangeDataStore: DataStore<Preferences> by preferencesDataStore(name = "exchange_config")

/**
 * DataStore-backed repository for ExchangeConfig settings.
 */
class ExchangeConfigRepository(private val context: Context) {

    private object PreferencesKeys {
        val CREDITS_PER_RUPEE = doublePreferencesKey("credits_per_rupee")
        val EXCHANGE_FEE_PERCENT = doublePreferencesKey("exchange_fee_percent")
    }

    val exchangeConfig: Flow<ExchangeConfig> = context.exchangeDataStore.data.map { preferences ->
        ExchangeConfig(
            creditsPerRupee = preferences[PreferencesKeys.CREDITS_PER_RUPEE] ?: 1.0,
            exchangeFeePercent = preferences[PreferencesKeys.EXCHANGE_FEE_PERCENT] ?: 0.0
        )
    }

    suspend fun updateExchangeConfig(config: ExchangeConfig) {
        context.exchangeDataStore.edit { preferences ->
            preferences[PreferencesKeys.CREDITS_PER_RUPEE] = config.creditsPerRupee
            preferences[PreferencesKeys.EXCHANGE_FEE_PERCENT] = config.exchangeFeePercent
        }
    }
}
