package com.claude.messages.data.repo

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import com.claude.messages.data.model.Contact
import com.claude.messages.util.PhoneNumbers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Looks up display names and photos for phone numbers, with a small in-memory cache. */
class ContactsRepository(private val context: Context) {

    private val nameCache = ConcurrentHashMap<String, Contact?>()

    suspend fun lookup(number: String): Contact? = withContext(Dispatchers.IO) {
        if (number.isBlank()) return@withContext null
        nameCache[number]?.let { return@withContext it }
        if (nameCache.containsKey(number)) return@withContext null

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number),
        )
        val contact = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.PhoneLookup._ID,
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.PHOTO_URI,
                ),
                null, null, null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    Contact(
                        id = c.getLong(0),
                        name = c.getString(1).orEmpty(),
                        number = number,
                        photoUri = c.getString(2),
                    )
                } else null
            }
        }.getOrNull()
        nameCache[number] = contact
        contact
    }

    suspend fun displayName(number: String): String =
        lookup(number)?.name?.takeIf { it.isNotBlank() } ?: formatNumber(number)

    /** All phone-book entries with a number, for the recipient picker. */
    suspend fun allContacts(): List<Contact> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Contact>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                ),
                null, null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
            )?.use { c ->
                val seen = mutableSetOf<String>()
                while (c.moveToNext()) {
                    val number = c.getString(2).orEmpty()
                    val key = PhoneNumbers.normalize(number)
                    if (key.isBlank() || !seen.add(key)) continue
                    out += Contact(
                        id = c.getLong(0),
                        name = c.getString(1).orEmpty(),
                        number = number,
                        photoUri = c.getString(3),
                    )
                }
            }
        }
        out
    }

    fun invalidate() = nameCache.clear()

    companion object {
        fun formatNumber(number: String): String =
            runCatching { PhoneNumberUtils.formatNumber(number, "US") }.getOrNull() ?: number

        fun normalize(number: String): String = PhoneNumbers.normalize(number)

        fun sameNumber(a: String, b: String): Boolean = PhoneNumbers.sameNumber(a, b)
    }
}
