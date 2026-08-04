package com.graywar.noServerManager.dbManager.Discord

import kotlin.collections.iterator

@JvmInline
value class TemplateString(val raw: LogFormat) {
    fun format(variables: Map<String, String>): String {
        var result = raw.format
        for ((key, value) in variables) {
            var nv = value
            if (raw.sanitizeForCode) nv = escapeForCodeBlock(nv)
            if (raw.sanitizeForDiscord) nv = escapeDiscordMarkdown(nv)
            result = result.replace("{$key}", nv)
        }
        return result
    }
}