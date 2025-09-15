package com.lessonarchiver.db

import org.koin.dsl.module

object Registrar {
    val tables = listOf(UserTable, FileTable)
}