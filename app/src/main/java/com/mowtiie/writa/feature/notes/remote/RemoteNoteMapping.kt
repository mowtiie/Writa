package com.mowtiie.writa.feature.notes.remote

import com.mowtiie.writa.feature.notes.Note

fun RemoteNote.toNote(): Note =
    Note(
        id = id,
        title = title,
        content = content,
        tags = tags.orEmpty(),
        pinned = pinned ?: false,
        archived = archived ?: false,
        createdAt = createdAt.orEmpty(),
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )
