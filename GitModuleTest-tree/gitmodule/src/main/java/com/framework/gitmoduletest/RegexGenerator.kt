package com.framework.gitmoduletest

// Used to Generate Regex
class RegexGenerator {

    // Generate Regex for a given string
    fun generateRegex(input: String): String {
        return input.replace(" ", "\\s*")
    }

}