package com.arflix.tv.util

/**
 * A language name, ISO 639-1/639-2 code or settings label ("Hebrew", "heb", "Portuguese (Brazil)",
 * "pt-BR") as the short code the players match tracks by ("he", "pt-br"). Shared by the VOD player
 * and Live TV so both read the profile's language settings the same way. Unknown input comes back
 * lower-cased.
 */
internal fun normalizeLanguageCode(lang: String): String {
    val lowerLang = lang.lowercase().trim()
    return when {
        lowerLang in listOf("ms", "msa", "may", "malay", "bahasa melayu") ||
            lowerLang.startsWith("ms-") || lowerLang.startsWith("ms_") -> "ms"
        // Full names
        lowerLang == "english" || lowerLang.startsWith("english") -> "en"
        lowerLang == "spanish" || lowerLang.startsWith("spanish") || lowerLang == "espanol" -> "es"
        lowerLang == "french" || lowerLang.startsWith("french") || lowerLang == "francais" -> "fr"
        lowerLang == "german" || lowerLang.startsWith("german") || lowerLang == "deutsch" -> "de"
        lowerLang == "italian" || lowerLang.startsWith("italian") -> "it"
        lowerLang == "portuguese" -> "pt"
        lowerLang == "portuguese (brazil)" ||
            lowerLang == "portuguese-brazil" ||
            lowerLang == "brazilian portuguese" ||
            lowerLang == "brazil portuguese" ||
            lowerLang == "pt-br" ||
            lowerLang == "ptbr" -> "pt-br"
        lowerLang.startsWith("portuguese") -> "pt"
        lowerLang == "dutch" || lowerLang.startsWith("dutch") -> "nl"
        lowerLang == "russian" || lowerLang.startsWith("russian") -> "ru"
        lowerLang == "chinese" || lowerLang.startsWith("chinese") -> "zh"
        lowerLang == "japanese" || lowerLang.startsWith("japanese") || lowerLang == "jp" || lowerLang == "jap" -> "ja"
        lowerLang == "korean" || lowerLang.startsWith("korean") -> "ko"
        lowerLang == "arabic" || lowerLang.startsWith("arabic") -> "ar"
        lowerLang == "hindi" || lowerLang.startsWith("hindi") -> "hi"
        lowerLang == "turkish" || lowerLang.startsWith("turkish") -> "tr"
        lowerLang == "polish" || lowerLang.startsWith("polish") -> "pl"
        lowerLang == "swedish" || lowerLang.startsWith("swedish") -> "sv"
        lowerLang == "norwegian" || lowerLang.startsWith("norwegian") -> "no"
        lowerLang == "danish" || lowerLang.startsWith("danish") -> "da"
        lowerLang == "finnish" || lowerLang.startsWith("finnish") -> "fi"
        lowerLang == "greek" || lowerLang.startsWith("greek") -> "el"
        lowerLang == "czech" || lowerLang.startsWith("czech") -> "cs"
        lowerLang == "hungarian" || lowerLang.startsWith("hungarian") -> "hu"
        lowerLang == "romanian" || lowerLang.startsWith("romanian") -> "ro"
        lowerLang == "thai" || lowerLang.startsWith("thai") -> "th"
        lowerLang == "vietnamese" || lowerLang.startsWith("vietnamese") -> "vi"
        lowerLang == "indonesian" || lowerLang.startsWith("indonesian") -> "id"
        lowerLang == "hebrew" || lowerLang.startsWith("hebrew") -> "he"
        lowerLang == "persian" || lowerLang.startsWith("persian") || lowerLang == "farsi" -> "fa"
        lowerLang == "ukrainian" || lowerLang.startsWith("ukrainian") -> "uk"
        lowerLang == "bengali" || lowerLang.startsWith("bengali") -> "bn"
        lowerLang == "bulgarian" || lowerLang.startsWith("bulgarian") -> "bg"
        lowerLang == "croatian" || lowerLang.startsWith("croatian") -> "hr"
        lowerLang == "serbian" || lowerLang.startsWith("serbian") -> "sr"
        lowerLang == "slovak" || lowerLang.startsWith("slovak") -> "sk"
        lowerLang == "slovenian" || lowerLang.startsWith("slovenian") -> "sl"
        lowerLang == "lithuanian" || lowerLang.startsWith("lithuanian") -> "lt"
        lowerLang == "estonian" || lowerLang.startsWith("estonian") -> "et"
        // ISO 639-1 codes (2 letter)
        lowerLang.length == 2 -> lowerLang
        // ISO 639-2 codes (3 letter)
        lowerLang == "eng" -> "en"
        lowerLang == "spa" -> "es"
        lowerLang == "fra" || lowerLang == "fre" -> "fr"
        lowerLang == "deu" || lowerLang == "ger" -> "de"
        lowerLang == "ita" -> "it"
        lowerLang == "por" -> "pt"
        lowerLang == "pob" || lowerLang == "pobr" -> "pt-br"
        lowerLang == "nld" || lowerLang == "dut" -> "nl"
        lowerLang == "rus" -> "ru"
        lowerLang == "zho" || lowerLang == "chi" -> "zh"
        lowerLang == "jpn" -> "ja"
        lowerLang == "kor" -> "ko"
        lowerLang == "ara" -> "ar"
        lowerLang == "hin" -> "hi"
        lowerLang == "tur" -> "tr"
        lowerLang == "pol" -> "pl"
        lowerLang == "swe" -> "sv"
        lowerLang == "nor" -> "no"
        lowerLang == "dan" -> "da"
        lowerLang == "fin" -> "fi"
        lowerLang == "ell" || lowerLang == "gre" -> "el"
        lowerLang == "ces" || lowerLang == "cze" -> "cs"
        lowerLang == "hun" -> "hu"
        lowerLang == "ron" || lowerLang == "rum" -> "ro"
        lowerLang == "tha" -> "th"
        lowerLang == "vie" -> "vi"
        lowerLang == "ind" -> "id"
        lowerLang == "heb" || lowerLang == "iw" -> "he"
        lowerLang == "fas" || lowerLang == "per" -> "fa"
        lowerLang == "ukr" -> "uk"
        lowerLang == "ben" -> "bn"
        lowerLang == "bul" -> "bg"
        lowerLang == "hrv" -> "hr"
        lowerLang == "srp" -> "sr"
        lowerLang == "slk" || lowerLang == "slo" -> "sk"
        lowerLang == "slv" -> "sl"
        lowerLang == "lit" -> "lt"
        lowerLang == "est" -> "et"
        else -> lowerLang
    }
}
