package com.rohansingh.focusforge.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.rohansingh.focusforge.data.entities.Wallet
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Wallet operations.
 */
@Dao
interface WalletDao {
    @Query("SELECT * FROM wallet WHERE id = 1")
    fun getWallet(): Flow<Wallet?>

    @Query("SELECT * FROM wallet WHERE id = 1")
    suspend fun getWalletOnce(): Wallet?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallet(wallet: Wallet): Long

    @Update
    suspend fun updateWallet(wallet: Wallet): Int
}
