package com.localpush.plugin.model

/** Severity of a log line; the tool window maps each level to a theme-aware console color. */
enum class LogLevel { INFO, SUCCESS, WARNING, ERROR }

data class LogMessage(val level: LogLevel, val text: String)
