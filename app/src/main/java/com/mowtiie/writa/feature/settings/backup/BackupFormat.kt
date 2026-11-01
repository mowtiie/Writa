package com.mowtiie.writa.feature.settings.backup

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import android.util.MalformedJsonException
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PushbackReader
import java.time.Instant
import javax.inject.Inject

/** Reads and writes the backup file as a stream. The caller owns, and closes, the streams. */
interface BackupCodec {
    @Throws(IOException::class)
    fun write(
        out: OutputStream,
        notes: List<BackupNote>,
        exportedAt: Instant,
    )

    /** @throws InvalidBackupException @throws UnsupportedBackupVersionException @throws IOException */
    @Throws(IOException::class)
    fun read(input: InputStream): List<BackupNote>
}

/**
 * The JSON backup file:
 * `{"version":1,"exportedAt":<epoch ms>,"notes":[{"title","content","createdAt","tags"?,"pinned"?,"archived"?}]}`.
 * Both directions stream, so a large backup never exists as one string. The reader accepts the keys in any order
 * (`version` may follow `notes`) and ignores what it does not know. The file is plain text: it is not encrypted.
 */
class BackupFormat
    @Inject
    constructor() : BackupCodec {
        override fun write(
            out: OutputStream,
            notes: List<BackupNote>,
            exportedAt: Instant,
        ) {
            val writer = JsonWriter(BufferedWriter(OutputStreamWriter(out, Charsets.UTF_8)))
            writer.setIndent("  ")
            writer.beginObject()
            writer.name(KEY_VERSION).value(BackupRules.VERSION.toLong())
            writer.name(KEY_EXPORTED_AT).value(exportedAt.toEpochMilli())
            writer.name(KEY_NOTES).beginArray()
            notes.forEach { writeNote(writer, it) }
            writer.endArray()
            writer.endObject()
            writer.flush()
        }

        private fun writeNote(
            writer: JsonWriter,
            note: BackupNote,
        ) {
            writer.beginObject()
            writer.name(KEY_TITLE).value(note.title)
            writer.name(KEY_CONTENT).value(note.content)
            note.createdAt?.let { writer.name(KEY_CREATED_AT).value(it) }
            if (note.pinned) writer.name(KEY_PINNED).value(true)
            if (note.archived) writer.name(KEY_ARCHIVED).value(true)
            if (note.tags.isNotEmpty()) {
                writer.name(KEY_TAGS).beginArray()
                note.tags.forEach { writer.value(it) }
                writer.endArray()
            }
            writer.endObject()
        }

        override fun read(input: InputStream): List<BackupNote> =
            try {
                val text = PushbackReader(BufferedReader(InputStreamReader(input, Charsets.UTF_8)), 1)
                requireObjectAhead(text)
                BackupRules.interpret(readRaw(JsonReader(text)))
            } catch (e: IllegalStateException) {
                throw InvalidBackupException("Not a backup file", e)
            } catch (e: NumberFormatException) {
                throw InvalidBackupException("Not a backup file", e)
            } catch (e: MalformedJsonException) {
                throw InvalidBackupException("Not a backup file", e)
            } catch (e: EOFException) {
                throw InvalidBackupException("The backup file is cut off", e)
            }

        /**
         * A backup is a JSON object. Checking the first character here (after any byte-order mark and spaces, which
         * Windows editors add) is what tells a foreign file apart from a real read error: the stream reader reports
         * a document like `null` or `42` as a plain `IOException`.
         */
        private fun requireObjectAhead(text: PushbackReader) {
            var next = text.read()
            while (next == BYTE_ORDER_MARK || (next != -1 && next.toChar().isWhitespace())) next = text.read()
            if (next == -1) throw EOFException("Empty file")
            if (next != OBJECT_START) throw InvalidBackupException("Not a backup file")
            text.unread(next)
        }

        private fun readRaw(reader: JsonReader): RawBackup {
            var version: Int? = null
            var notes: List<RawBackupNote>? = null
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    KEY_VERSION -> version = reader.nextInt()
                    KEY_NOTES -> notes = readNotesIfArray(reader, notes)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            return RawBackup(version, notes)
        }

        /** The entries of the `notes` array, added to [soFar]; null stays null for a `notes` value that is no array. */
        private fun readNotesIfArray(
            reader: JsonReader,
            soFar: List<RawBackupNote>?,
        ): List<RawBackupNote>? {
            if (reader.peek() != JsonToken.BEGIN_ARRAY) {
                reader.skipValue()
                return soFar
            }
            return soFar.orEmpty() + readNotes(reader)
        }

        private fun readNotes(reader: JsonReader): List<RawBackupNote> {
            val notes = mutableListOf<RawBackupNote>()
            reader.beginArray()
            while (reader.hasNext()) {
                if (reader.peek() == JsonToken.BEGIN_OBJECT) notes += readNote(reader) else reader.skipValue()
            }
            reader.endArray()
            return notes
        }

        private fun readNote(reader: JsonReader): RawBackupNote {
            var title: String? = null
            var content: String? = null
            var createdAt: String? = null
            var pinned: Boolean? = null
            var archived: Boolean? = null
            var tags: List<String>? = null
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                val token = reader.peek()
                when {
                    name == KEY_TITLE && token == JsonToken.STRING -> title = reader.nextString()
                    name == KEY_CONTENT && token == JsonToken.STRING -> content = reader.nextString()
                    name == KEY_CREATED_AT && token == JsonToken.STRING -> createdAt = reader.nextString()
                    name == KEY_PINNED && token == JsonToken.BOOLEAN -> pinned = reader.nextBoolean()
                    name == KEY_ARCHIVED && token == JsonToken.BOOLEAN -> archived = reader.nextBoolean()
                    name == KEY_TAGS && token == JsonToken.BEGIN_ARRAY -> tags = readTags(reader)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            return RawBackupNote(title, content, createdAt, pinned, archived, tags)
        }

        /** Only the text entries of the array are tags. */
        private fun readTags(reader: JsonReader): List<String> {
            val tags = mutableListOf<String>()
            reader.beginArray()
            while (reader.hasNext()) {
                if (reader.peek() == JsonToken.STRING) tags += reader.nextString() else reader.skipValue()
            }
            reader.endArray()
            return tags
        }

        private companion object {
            const val BYTE_ORDER_MARK = 0xFEFF
            const val OBJECT_START = 0x7B // {
            const val KEY_VERSION = "version"
            const val KEY_EXPORTED_AT = "exportedAt"
            const val KEY_NOTES = "notes"
            const val KEY_TITLE = "title"
            const val KEY_CONTENT = "content"
            const val KEY_CREATED_AT = "createdAt"
            const val KEY_TAGS = "tags"
            const val KEY_PINNED = "pinned"
            const val KEY_ARCHIVED = "archived"
        }
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {
    @Binds
    abstract fun bindBackupCodec(format: BackupFormat): BackupCodec
}
