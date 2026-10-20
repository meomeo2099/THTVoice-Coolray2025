package com.voicegeely.app.voice

import java.text.Normalizer
import java.util.Locale

/**
 * Spoken app names → installed apps. Pure logic so it is unit-testable; the Android
 * side only supplies the (label, package) list from PackageManager.
 */
object InstalledAppMatcher {
    data class InstalledApp(val label: String, val packageName: String)

    /**
     * ASR spellings seen in the reference voice dataset, mapped to a name that matches
     * the app label or package. Applied only to the app-name slot of "mở …" commands.
     */
    private val spokenAliases = linkedMapOf(
        // 09/10/2026 — common apps as Sherpa hears them; longer names first.
        "du túp miu dích" to "youtube music", "diu túp miu dích" to "youtube music", "du túp mu dích" to "youtube music",
        "youtube miu dích" to "youtube music", "youtube miu díc" to "youtube music", "youtube mu dích" to "youtube music",
        "diu túp" to "youtube", "giu túp" to "youtube", "yêu túp" to "youtube", "iu túp" to "youtube", "dù túp" to "youtube",
        "du tuýp" to "youtube", "diu tuýp" to "youtube", "ru túp" to "youtube", "du tu" to "youtube", "you tu" to "youtube",
        "gú gồ mát" to "google maps", "goo gồ mát" to "google maps", "gu mát" to "google maps", "gú mát" to "google maps",
        "gù mát" to "google maps", "gút gồ mát" to "google maps", "gúc gồ mát" to "google maps", "gu gồ máp" to "google maps",
        "gu gồ mắp" to "google maps", "gu gồ" to "google maps", "gú gồ" to "google maps", "bản đồ gu gờ" to "google maps",
        "việt máp" to "vietmap", "việt mép" to "vietmap", "viết map" to "vietmap", "viết mát" to "vietmap", "vi ét map" to "vietmap",
        "việt ma lai" to "vietmap", "việt mát lai" to "vietmap",
        "quây dơ" to "waze", "guây dơ" to "waze", "uây dơ" to "waze", "goay dơ" to "waze",
        "sờ pô ti phai" to "spotify", "sì pô ti phai" to "spotify", "pô ti phai" to "spotify", "spô ti phai" to "spotify",
        "sờ pốt ti phai" to "spotify", "sport ti phai" to "spotify", "spoti phai" to "spotify", "sờ pót ti phai" to "spotify",
        "din em pê ba" to "zing mp3", "dinh em pê ba" to "zing mp3", "zing em pê ba" to "zing mp3", "din mp3" to "zing mp3",
        "ding mp3" to "zing mp3", "zin mp3" to "zing mp3", 
        "tít tốc" to "tiktok", "tíc tóc" to "tiktok", "tích tóc" to "tiktok", "tik tốc" to "tiktok", "tí tốc" to "tiktok",
        "tích tắc" to "tiktok", "tít tóc" to "tiktok",
        "nét phờ lích" to "netflix", "nét phơ lích" to "netflix", "nét phờ líc" to "netflix", "nét flix" to "netflix",
        "nét phlích" to "netflix", "nét phích" to "netflix",
        "crôm" to "chrome", "cờ rôm" to "chrome", "khờ rôm" to "chrome", "crom" to "chrome", "gờ rôm" to "chrome",
        "vê lê xê" to "vlc", "vi eo xi" to "vlc", "vê en xê" to "vlc",
        "ki ky" to "kiki", "ky ky" to "kiki", "kì ki" to "kiki", "kí ki" to "kiki",
        "za lô" to "zalo", "dá lồ" to "zalo", "da lồ" to "zalo", "gia lồ" to "zalo",
        "phây búc" to "facebook", "phê búc" to "facebook", "phay búc" to "facebook", "phết búc" to "facebook",
        "mét xen giơ" to "messenger", "mét sen giơ" to "messenger", "mét xen gơ" to "messenger",
        "gờ meo" to "gmail", "gì meo" to "gmail", "gi meo" to "gmail", "chi meo" to "gmail",
        "sóp pi" to "shopee", "sốp pi" to "shopee", "shop pi" to "shopee", "sô pi" to "shopee",
        "gờ ráp" to "grab", "gờ rắp" to "grab", "gráp" to "grab",
        "chợ play" to "play store", "plây sto" to "play store", "plây xì to" to "play store", "cửa hàng play" to "play store",
        "ép pi tê play" to "fpt play", "ép bê tê play" to "fpt play",
        "du túp miu díc" to "youtube music", "iu túp miu díc" to "youtube music",
        "du túp" to "youtube", "you túp" to "youtube", "du tu be" to "youtube",
        "gu gồ mát" to "google maps", "gu gờ mát" to "google maps", "bản đồ gu gồ" to "google maps",
        "việt mắp" to "vietmap", "việt map" to "vietmap", "việt mát" to "vietmap", "việt mấp" to "vietmap",
        "việc map" to "vietmap", "việc mát" to "vietmap",
        "quê dơ" to "waze", "vê dơ" to "waze",
        "sờ mát tuýp" to "smarttube", "mát tuýp" to "smarttube", "sờ ma túp" to "smarttube", "sờ mát túp" to "smarttube",
        "sờ mát tu be" to "smarttube",
        "dinh mp3" to "zing mp3", "sờ ti phai" to "spotify", "sốt ti phai" to "spotify", "sờ po ti phai" to "spotify",
        "tíc tốc" to "tiktok", "tích tốc" to "tiktok", "nét phích" to "netflix", "nét lít" to "netflix",
        "cờ rôm" to "chrome", "vê lờ xê" to "vlc", "vê eo xê" to "vlc", "ki ki" to "kiki",
        "gia lô" to "zalo", "da lô" to "zalo",
        "ép phê tê" to "fpt play", "ép pê tê" to "fpt play",
        "vê tê vê gô" to "vtv go", "vê tê vê go" to "vtv go", "vê tê vê" to "vtv",
        "tê vê 360" to "tv360", "ti vi 360" to "tv360", "tv 360" to "tv360",
        "na vi ten" to "navitel", "na vi teo" to "navitel", "gô fa" to "gofa", "gô pha" to "gofa",
        "em ích play ơ" to "mx player", "mờ ích" to "mx player", "vi on" to "vieon",
        "nhạc của tui" to "nhaccuatui", "nct" to "nhaccuatui",
        "âu rét" to "aures", "ao rét" to "aures",
        "quản lý tệp tin" to "file manager", "quản lý tệp" to "file manager", "quản lý file" to "file manager",
        "đồng hồ tốc độ" to "speed display", "hiển thị tốc độ" to "speed display",
        "car play" to "carplay"
    )

    fun canonicalName(spoken: String): String {
        var value = " " + spoken.trim().lowercase(Locale("vi", "VN")) + " "
        spokenAliases.forEach { (from, to) -> value = value.replace(" $from ", " $to ") }
        return value.trim()
    }

    /** Packages whose label (or package id) matches the spoken name, best match first. */
    fun match(spokenName: String, apps: List<InstalledApp>): List<String> {
        val wanted = key(canonicalName(spokenName))
        if (wanted.length < 2) return emptyList()
        val exact = apps.filter { key(it.label) == wanted }
        if (exact.isNotEmpty()) return exact.map { it.packageName }
        // Short spoken names only match the start of a label, so "mat" never picks "Format …".
        val labelContains = apps.filter { key(it.label).let { label -> (wanted.length >= 3 && label.startsWith(wanted)) || (wanted.length >= 4 && label.contains(wanted)) } }
        if (labelContains.isNotEmpty()) return labelContains.sortedBy { it.label.length }.map { it.packageName }
        val nameContainsLabel = apps.filter { key(it.label).length >= 4 && wanted.contains(key(it.label)) }
        if (nameContainsLabel.isNotEmpty()) return nameContainsLabel.sortedByDescending { it.label.length }.map { it.packageName }
        if (wanted.length >= 4) {
            val byPackage = apps.filter { it.packageName.lowercase(Locale.ROOT).replace(".", "").contains(wanted) }
            if (byPackage.isNotEmpty()) return byPackage.map { it.packageName }
        }
        return emptyList()
    }

    /** Lowercase, no diacritics, no spaces/punctuation: "Zing MP3" → "zingmp3". */
    fun key(value: String): String = Normalizer.normalize(value.lowercase(Locale("vi", "VN")), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace('đ', 'd')
        .replace(Regex("[^a-z0-9]"), "")
}
