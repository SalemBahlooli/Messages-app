package com.claude.messages.di

import android.content.Context
import com.claude.messages.data.db.AppDatabase
import com.claude.messages.data.repo.ContactsRepository
import com.claude.messages.data.repo.SmsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Tiny manual dependency container. Broadcast receivers and services are
 * constructed by the framework, so they need a process-wide way to reach the
 * repositories without pulling in a full DI framework.
 */
object ServiceLocator {

    @Volatile private var smsRepo: SmsRepository? = null
    @Volatile private var contactsRepo: ContactsRepository? = null

    private val _dataChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    /** Emits whenever the SMS provider was written to, so open screens refresh. */
    val dataChanged: SharedFlow<Unit> = _dataChanged

    fun smsRepository(context: Context): SmsRepository = smsRepo ?: synchronized(this) {
        smsRepo ?: SmsRepository(context.applicationContext).also { smsRepo = it }
    }

    fun contactsRepository(context: Context): ContactsRepository =
        contactsRepo ?: synchronized(this) {
            contactsRepo ?: ContactsRepository(context.applicationContext).also { contactsRepo = it }
        }

    fun database(context: Context): AppDatabase = AppDatabase.get(context)

    fun notifyDataChanged() {
        _dataChanged.tryEmit(Unit)
    }
}
