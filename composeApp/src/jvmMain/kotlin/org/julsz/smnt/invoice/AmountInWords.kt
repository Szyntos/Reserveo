package org.julsz.smnt.invoice

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object AmountInWords {

    fun convert(amount: Double, locale: Locale): String {
        val rounded   = (abs(amount) * 100).roundToLong()
        val zloty     = rounded / 100
        val grosze    = (rounded % 100).toInt()
        val negative  = amount < 0.0

        val zlotyWords  = numberToWords(zloty, locale)
        val groszeWords = if (grosze > 0) {
            val gw = numberToWords(grosze.toLong(), locale)
            val gf = if (locale.language == "pl") groszeForm(grosze) else if (grosze == 1) "grosz" else "groszy"
            " $gw $gf"
        } else {
            ""
        }

        val zlotyCurrency = if (locale.language == "pl") zlotyForm(zloty) else if (zloty == 1L) "zloty" else "zlotych"
        val result = "$zlotyWords $zlotyCurrency$groszeWords"
        return if (negative) "minus $result" else result.replaceFirstChar { it.uppercaseChar() }
    }

    private fun zlotyForm(n: Long): String {
        val abs = abs(n)
        if (abs == 1L) return "złoty"
        val lastTwo = abs % 100
        val lastOne = abs % 10
        return when {
            lastTwo in 12..14 -> "złotych"
            lastOne in 2..4   -> "złote"
            else              -> "złotych"
        }
    }

    private fun numberToWords(n: Long, locale: Locale): String {
        if (n == 0L) return if (locale.language == "pl") "zero" else "zero"
        return buildWords(n, locale).trim()
    }

    private fun buildWords(n: Long, locale: Locale): String {
        if (n == 0L) return ""
        val pl = locale.language == "pl"

        val ones = if (pl) arrayOf(
            "", "jeden", "dwa", "trzy", "cztery", "pięć", "sześć", "siedem", "osiem", "dziewięć",
            "dziesięć", "jedenaście", "dwanaście", "trzynaście", "czternaście", "piętnaście",
            "szesnaście", "siedemnaście", "osiemnaście", "dziewiętnaście"
        ) else arrayOf(
            "", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen",
            "sixteen", "seventeen", "eighteen", "nineteen"
        )

        val tens = if (pl) arrayOf(
            "", "", "dwadzieścia", "trzydzieści", "czterdzieści", "pięćdziesiąt",
            "sześćdziesiąt", "siedemdziesiąt", "osiemdziesiąt", "dziewięćdziesiąt"
        ) else arrayOf(
            "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
        )

        val hundreds = if (pl) arrayOf(
            "", "sto", "dwieście", "trzysta", "czterysta", "pięćset",
            "sześćset", "siedemset", "osiemset", "dziewięćset"
        ) else arrayOf(
            "", "one hundred", "two hundred", "three hundred", "four hundred", "five hundred",
            "six hundred", "seven hundred", "eight hundred", "nine hundred"
        )

        return when {
            n >= 1_000_000 -> {
                val millions = n / 1_000_000
                val rest     = n % 1_000_000
                val milWord  = if (pl) millionsForm(millions) else if (millions == 1L) "million" else "millions"
                "${buildWords(millions, locale)} $milWord ${buildWords(rest, locale)}".trim()
            }
            n >= 1_000 -> {
                val thousands = n / 1_000
                val rest      = n % 1_000
                val thWord    = if (pl) thousandsForm(thousands) else if (thousands == 1L) "thousand" else "thousands"
                val prefix    = if (pl && thousands == 1L) "tysiąc" else "${buildWords(thousands, locale)} $thWord"
                "$prefix ${buildWords(rest, locale)}".trim()
            }
            n >= 100 -> {
                val h    = (n / 100).toInt()
                val rest = n % 100
                "${hundreds[h]} ${buildWords(rest, locale)}".trim()
            }
            n >= 20 -> {
                val t    = (n / 10).toInt()
                val rest = (n % 10).toInt()
                "${tens[t]} ${if (rest > 0) ones[rest] else ""}".trim()
            }
            else -> ones[n.toInt()]
        }
    }

    private fun thousandsForm(n: Long): String {
        val abs    = abs(n)
        val lastTwo = abs % 100
        val lastOne = abs % 10
        return when {
            lastTwo in 12..14 -> "tysięcy"
            lastOne in 2..4   -> "tysiące"
            else              -> "tysięcy"
        }
    }

    private fun millionsForm(n: Long): String {
        val abs     = abs(n)
        val lastTwo = abs % 100
        val lastOne = abs % 10
        return when {
            lastTwo in 12..14 -> "milionów"
            lastOne in 2..4   -> "miliony"
            else              -> "milionów"
        }
    }

    private fun groszeForm(n: Int): String {
        val lastTwo = n % 100
        val lastOne = n % 10
        return when {
            lastTwo in 12..14 -> "groszy"
            lastOne == 1      -> "grosz"
            lastOne in 2..4   -> "grosze"
            else              -> "groszy"
        }
    }
}
