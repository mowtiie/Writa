package com.mowtiie.writa.core.database

object NoteIds {
    /** The editor's id for "no note", so it is never a real or temporary id. */
    const val NO_NOTE = -1L

    /** The first temporary id; further offline notes count down from here. */
    const val FIRST_LOCAL = -2L
}
