package com.example.vssh

object SshCommandSafety {
    private val allowedPatterns = listOf(
        Regex("^journalctl\\b.*", RegexOption.IGNORE_CASE),
        Regex("^lastb?(\\s|$).*") ,
        Regex("^(who|w|uptime|whoami|id)(\\s|$).*") ,
        Regex("^uname\\s+-a(\\s|$).*", RegexOption.IGNORE_CASE),
        Regex("^systemctl\\s+(status|--failed)\\b.*", RegexOption.IGNORE_CASE),
        Regex("^(df\\s+-h|free\\s+-(m|h))(\\s|$).*") ,
        Regex("^(ss|netstat)\\s+-tulpn\\b.*", RegexOption.IGNORE_CASE),
        Regex("^ps\\s+aux\\b.*", RegexOption.IGNORE_CASE),
        Regex("^top\\s+-b\\s+-n\\s+1\\b.*", RegexOption.IGNORE_CASE),
        Regex("^tail\\s+-n\\s+\\d+\\s+/var/log/.*", RegexOption.IGNORE_CASE),
        Regex("^head\\s+-n\\s+\\d+\\s+/var/log/.*", RegexOption.IGNORE_CASE),
        Regex("^cat\\s+/var/log/.*", RegexOption.IGNORE_CASE),
        Regex("^grep\\s+.*\\s+/var/log/.*", RegexOption.IGNORE_CASE),
        Regex("^sed\\s+-n\\s+['\"].*['\"]\\s+/var/log/.*", RegexOption.IGNORE_CASE),
        Regex("^awk\\s+['\"].*['\"]\\s+/var/log/.*", RegexOption.IGNORE_CASE)
    )

    fun filter(commands: List<String>): List<String> {
        return commands
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { normalize(it) }
            .filter { isSafe(it) }
    }

    private fun isSafe(command: String): Boolean {
        if (command.contains(";") || command.contains("&&") || command.contains("|")) return false
        val stripped = command.removePrefix("sudo -n ").trim()
        return allowedPatterns.any { it.matches(stripped) }
    }

    private fun normalize(command: String): String? {
        val trimmed = command.trim()
        val hasSudo = trimmed.startsWith("sudo ")
        var rest = if (hasSudo) trimmed.removePrefix("sudo ").trim() else trimmed
        if (rest.isBlank()) return null

        rest = when {
            rest.startsWith("journalctl") -> normalizeJournalctl(rest)
            rest.startsWith("lastb") -> normalizeLast(rest, "lastb")
            rest.startsWith("last") -> normalizeLast(rest, "last")
            else -> rest
        }

        return if (hasSudo) "sudo -n $rest" else rest
    }

    private fun normalizeJournalctl(cmd: String): String {
        var result = cmd
        if (!result.contains("--no-pager")) {
            result += " --no-pager"
        }
        val hasLimit = result.contains(" -n ") || result.contains("--lines")
        if (!hasLimit) {
            result += " -n 200"
        }
        return result
    }

    private fun normalizeLast(cmd: String, base: String): String {
        if (!cmd.startsWith(base)) return cmd
        if (cmd.contains(" -n ")) return cmd
        return "$cmd -n 50"
    }
}
