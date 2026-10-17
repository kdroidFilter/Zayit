package io.github.kdroidfilter.seforimapp.framework.search

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository

/** The repository of the database at [databasePath] (the desktop app keeps its own pooled driver). */
fun openSeforimRepository(databasePath: String): SeforimRepository =
    SeforimRepository(databasePath, JdbcSqliteDriver("jdbc:sqlite:$databasePath"))
