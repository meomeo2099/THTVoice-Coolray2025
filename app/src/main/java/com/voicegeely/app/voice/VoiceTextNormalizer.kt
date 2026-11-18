package com.voicegeely.app.voice

import com.voicegeely.app.parsers.VietnameseNumberParser
import java.text.Normalizer
import java.util.Locale

/** Normalizes Vietnamese ASR output before intent/slot matching. */
object VoiceTextNormalizer {
    private val replacements = linkedMapOf(
        // Common ASR slips for "điều hòa".
        "đều hoà" to "điều hòa",
        "đều hòa" to "điều hòa",
        "điều hoà" to "điều hòa",
        "máy lạnh" to "điều hòa",
        "tỉnh nhiệt độ" to "chỉnh nhiệt độ",
        "tính nhiệt độ" to "chỉnh nhiệt độ",
        "cảnh nhiệt độ" to "chỉnh nhiệt độ",
        "đạt nhiệt độ" to "đặt nhiệt độ",
        "ấm lên" to "ấm hơn",
        "nóng lên" to "nóng hơn",
        "mát xuống" to "mát hơn",
        "mát lên" to "mát hơn",
        "lạnh xuống" to "lạnh hơn",
        "hạ nhiệt độ" to "giảm nhiệt độ",
        "hạ nhiệt" to "giảm nhiệt",
        "nâng nhiệt độ" to "tăng nhiệt độ",
        "nâng nhiệt" to "tăng nhiệt",
        "hạ điều hòa" to "giảm điều hòa",
        "nâng điều hòa" to "tăng điều hòa",

        // Fan aliases collapse to the same relative command path.
        "quạt số" to "quạt mức",
        "gió số" to "mức gió",
        "gió mức" to "mức gió",
        "tăng gió" to "tăng quạt",
        "giảm gió" to "giảm quạt",
        "hạ gió" to "giảm quạt",
        "hạ quạt" to "giảm quạt",
        "quạt to lên" to "tăng quạt",
        "cho quạt to lên" to "tăng quạt",
        "quạt mạnh hơn" to "tăng quạt",
        "gió mạnh hơn" to "tăng quạt",
        "quạt to hơn" to "tăng quạt",
        "gió to hơn" to "tăng quạt",
        "quạt nhẹ hơn" to "giảm quạt",
        "gió nhẹ hơn" to "giảm quạt",
        "quạt nhỏ hơn" to "giảm quạt",
        "gió nhỏ hơn" to "giảm quạt",
        "gió mạnh nhất" to "quạt mạnh nhất",
        "gió yếu nhất" to "quạt yếu nhất",
        "gió tối đa" to "quạt tối đa",
        "gió tối thiểu" to "quạt tối thiểu",

        // Recirculation / defrost / airflow aliases.
        "lấy khí trong" to "lấy gió trong",
        "lấy khí ngoài" to "lấy gió ngoài",
        "lấy không khí trong" to "lấy gió trong",
        "lấy không khí ngoài" to "lấy gió ngoài",
        "lấy không khí bên ngoài" to "lấy gió ngoài",
        "khử sương" to "phá sương",
        "khử mờ kính" to "phá sương kính",
        "làm tan sương" to "phá sương",
        "hút ẩm kính" to "phá sương kính",
        "làm khô kính" to "phá sương kính",
        "gió mặt" to "gió vào mặt",
        "gió chân" to "gió xuống chân",

        // Navigation aliases. Provider names and the dynamic destination remain
        // intact; only the leading intent phrase is canonicalized.
        "tìm đường" to "dẫn đường",
        "điều hướng" to "dẫn đường",
        "đưa tôi tới" to "đi tới",
        "đưa tôi đến" to "đi đến",
        "chở tôi tới" to "đi tới",
        "chở tôi đến" to "đi đến",
        "lái xe tới" to "đi tới",
        "lái xe đến" to "đi đến",
        "bản đồ tới" to "đi tới",
        "bản đồ đến" to "đi đến",

        // Drive-mode synonyms.
        "chế độ lái bình thường" to "chế độ lái comfort",
        "chế độ lái normal" to "chế độ lái comfort",
        "chế độ lái thường" to "chế độ lái comfort",
        "chế độ lái êm ái" to "chế độ lái comfort",
        "chuyển sang normal" to "chế độ lái comfort",
        "chuyển sang comfort" to "chế độ lái comfort",
        "chuyển sang eco" to "chế độ lái eco",
        "chuyển sang sport" to "chế độ lái sport",
        "chuyển comfort" to "chế độ lái comfort",
        "chuyển eco" to "chế độ lái eco",
        "chuyển sport" to "chế độ lái sport",

        // Regeneration synonyms/numeric variants.
        "hồi năng lượng số " to "hồi năng lượng mức ",
        "hồi năng lượng cấp " to "hồi năng lượng mức ",
        "phanh tái sinh số " to "phanh tái sinh mức ",
        "phanh tái sinh cấp " to "phanh tái sinh mức ",
        "hồi năng lượng lớn nhất" to "hồi năng lượng cao",
        "hồi năng lượng mạnh nhất" to "hồi năng lượng cao",
        "hồi năng lượng nhỏ nhất" to "hồi năng lượng thấp",
        "hồi năng lượng nhẹ nhất" to "hồi năng lượng thấp",

        // Window vocabulary and multi-window phrases.
        "cửa cánh" to "cửa kính",
        "cửa xổ" to "cửa sổ",
        "kiếng" to "kính",
        "hết cửa kính" to "tất cả cửa kính",
        "hết kính" to "tất cả kính",
        "bốn cửa kính" to "4 kính",
        "4 cửa kính" to "4 kính",
        "1 nửa" to "50 phần trăm",
        "phía sau bên trái và phải" to "phía sau",
        "sau bên trái và phải" to "phía sau",
        "trái và phải phía sau" to "phía sau",
        "phía trước bên trái và phải" to "phía trước",
        "trước bên trái và phải" to "phía trước",
        "trái và phải phía trước" to "phía trước",
        "sau trái và phải" to "phía sau",
        "trước trái và phải" to "phía trước",

        // Keep longer passenger aliases before shorter driver aliases. This
        // avoids turning "tài phụ" into the driver side by partial replacement.
        "kính bên tài phụ" to "kính bên phụ",
        "kính tài phụ" to "kính bên phụ",
        "bên tài phụ" to "bên phụ",
        "ghế tài phụ" to "ghế phụ",
        "kính ghế phụ" to "kính bên phụ",
        "kính ghế lái" to "kính bên lái",
        "kính tài xế" to "kính bên lái",
        "kính tài" to "kính bên lái",
        "kính lái" to "kính bên lái",
        "kính phụ" to "kính bên phụ",
        "ghế tài" to "ghế lái",
        "bên tài" to "bên lái"
    )

    private val standaloneClimateSetpoint = Regex(
        "^(1[6-9]|2[0-9]|3[0-2])(?:\\s*(?:độ(?:\\s*c)?|c))?$"
    )
    // "22 rưỡi", "15 rưỡi", "22 phẩy 5" as a whole utterance = half-degree setpoint (Coolray).
    private val standaloneHalfSetpoint = Regex(
        "^(1[5-9]|2[0-9]|3[0-2])\\s*(rưỡi|(?:phẩy|chấm)\\s*5)(?:\\s*(?:độ(?:\\s*c)?|c))?$"
    )

    fun normalize(text: String): String {
        var value = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .lowercase(Locale("vi", "VN"))
            .trim()

        // "cho tui hai hai độ": drop the request prefix before numbers are read.
        value = value.replace(Regex("^cho (?:tôi|tui|mình|em) "), "")
        value = colloquialNumbers(value)
        value = VietnameseNumberParser.parseVietnameseNumber(value)
        value = fixOwnerReportedAsrSlips(value)
        value = normalizeRegionalVehicleWords(value)
        value = fixWindowNounSlips(value)

        replacements.forEach { (wrong, correct) ->
            value = value.replace(wrong, correct)
        }

        val sanitized = value
            .replace(Regex("[^\\p{L}\\p{N}%]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return canonicalizeStandaloneClimateShorthand(canonicalizeWindowVerbs(sanitized))
    }

    private const val B = "(?<![\\p{L}\\p{N}])"
    private const val E = "(?![\\p{L}\\p{N}])"
    /** "chế độ ê cô điều hòa" is the climate Eco, not a drive mode. */
    private const val NOT_HVAC = "(?! (?:điều hòa|điều hoà|máy lạnh|điều hòa không khí))"
    private const val DRIVE_LEAD = "(?:chế độ(?: lái)?|chuyển(?: sang)?(?: chế độ(?: lái)?)?|đổi(?: sang)?(?: chế độ(?: lái)?)?)"
    // Whole fan utterances only: never rewrite "hoạt động", app titles or destinations.
    private const val FAN_ASR_TAIL = "(?:(?: mức| số| cấp| nấc| lên| xuống| to| nhỏ| mạnh| yếu| lớn| cao| thấp| tối| đa| thiểu| nhất| hơn| nữa| thêm| bớt| gió| [0-9]+))*[.!?,]*$"
    private const val SCENE_SLIP_LEAD = "(?:(?:bật|mở|vào|chuyển(?: sang)?) )?(?:(?:chế độ|chế đô|chế đỗ|chế đổ|chế độc|chề độ|thế độ|chế dộ) )?"
    private val sharedCampingSlip = Regex("^$SCENE_SLIP_LEAD(?:đi )?(?:cắm|cấm|căm|cắn|cám|gắm|cằm) (?:trại|chại|trải|trạy|tại|chải|trai)$")
    private val sharedRestSlip = Regex("^$SCENE_SLIP_LEAD(?:(?:nghĩ|ngỉ|nghỉ|ngĩ|nghị) (?:ngơi|ngời|ngởi|ngơ|ngới)|thư dãn|thư giản|thư dản|thu giãn|thư giãn)(?: trong xe)?$")

    /**
     * ASR slips reported by the owner from real use (29/09/2026). Whole-utterance or
     * word-bounded only, so they cannot leak into unrelated commands.
     */
    private val ownerAsrSlips: List<Pair<Regex, String>> = listOf(
        // Verbs at the start of a command.
        Regex("^tanh$E") to "tăng",
        Regex("^tan (gió|quạt|nhiệt)$E") to "tăng $1",
        Regex("^bạc$E") to "bật",
        Regex("^tác$E") to "tắt",
        // "đóng" heard as "đám" (03/10/2026) — only before something that can be closed.
        Regex("${B}đám(?= (?:kính|cánh|cửa|cốp|hết|tất cả|cả|lại|rèm|ứng dụng|app)$E)") to "đóng",
        // Lights: "bạc nè", "đènnội thất" and cabin-light names use the ambient light path.
        Regex("${B}(bật|tắt|mở) nè$E") to "$1 đèn",
        Regex("đènnội") to "đèn nội",
        Regex("${B}(?:đèn led nội thất|led nội thất|đèn nội thất|đèn trong xe|đèn cabin|đèn nền|đèn trang trí|đèn dải led|đèn led viền|đèn led xe|đèn màu)$E(?! khi)") to "đèn viền",
        Regex("^(bật|tắt|mở)(?: giúp tôi)? đèn(?: lên| đi)?$") to "$1 đèn viền",
        // Defrost.
        Regex("^(?:sai|sấy) (?:can|cừ|cứ|cánh|kinh|kính)$") to "sấy kính",
        // Windows: "cánh" is heard for "kính", "lấy" for "lái", "ghé" for "ghế".
        Regex("${B}ghé (lấy|lái|phụ)$E") to "ghế $1",
        Regex("${B}(ghế|bên|kính|cánh|cửa sổ|tài) lấy$E") to "$1 lái",
        Regex("^(?:he|hé) (?:cam|cưng|kính|cánh)$") to "hé kính",
        Regex("${B}cánh$E") to "kính",
        // Fan (01/10/2026): "quạt" heard as "phạt" — only beside a fan verb or a fan level word.
        Regex("(?<=(?:bật|tắt|tăng|giảm|chỉnh|mở|đặt|để|cho) )phạt$E") to "quạt",
        Regex("${B}phạt(?= (?:mức|số|cấp|lên|xuống|to|nhỏ|mạnh|yếu|lớn|cao|thấp|tối|gió|\\d|một|hai|ba|bốn|năm|sáu|bảy|tám)$E)") to "quạt",
        Regex("^phạt$") to "quạt",
        // Owner 12/11/2026: offline ASR hears "quạt" as "hoạt".
        Regex("^(tăng|giảm|hạ|nâng|chỉnh|đặt|để|cho|bật|tắt|mở)( tốc độ)? hoạt(?=$FAN_ASR_TAIL)") to "$1$2 quạt",
        Regex("^(tốc độ )?hoạt(?= (?:mức|số|cấp|nấc|mạnh|yếu|to|nhỏ|tối|[0-9]+)(?: |[.!?,]*$))(?=$FAN_ASR_TAIL)") to "$1quạt",
        // Apps.
        Regex("${B}(?:you (?:tụ|tu|tú|tù|tủ) bay|diu tụ bay|you tụp bay|you tụi bay|youtu bay|you tụp|you túp|yoube|you tube|diu túp|yu túp)$E") to "youtube",
        // 09/10/2026: more YouTube / Spotify / Zing spellings, so media and "mở …" agree.
        Regex("${B}(?:du|diu|giu|yêu|iu|dù|ru) (?:túp|tuýp)$E") to "youtube",
        Regex("${B}youtube (?:miu|mu|mìu) (?:dích|díc|xích)$E") to "youtube music",
        Regex("${B}(?:sờ|sì|xờ)? ?(?:pô|pốt|pót|po) ti phai$E|${B}(?:spô ti phai|sport ti phai|spoti phai)$E") to "spotify",
        Regex("${B}(?:din|dinh|ding|zin|zing) (?:em (?:pê|pi|bê) (?:ba|3)|mp3|mp 3)$E") to "zing mp3",
        Regex("${B}(?:gu gồ mát|gu gờ mát|gu gồ map|gu gồ đáp|gu gờ đáp|gu bap|gu gồ bap|googlemap|google map)$E") to "google maps",
        // 09/10/2026: "gu mát", "gú gồ mát", "goo gồ mát", "gút gồ máp"…
        Regex("${B}(?:gu|gú|gù|gủ|goo|gô|gúc|gút|guc)(?: (?:gồ|gờ|gơ|gô|gố|gổ|go|gò))? (?:mát|máp|mắp|mép|mắt|mạp|map|maps|đáp|bap)$E") to "google maps",
        // 09/10/2026: "go gồ ma", "gâu gồ mép", "gầu gồ mát" — the middle "gồ" is required here.
        Regex("${B}(?:go|gò|gó|gâu|gầu|gấu|gau|gao|gồ|gu|gú|goo|gô|gúc|gút) (?:gồ|gờ|gơ|gô|gố|gổ|go|gò|gồ) (?:ma|mà|má|mát|máp|mắp|mép|mẹp|mếp|mắt|mạp|map|maps|mấp)$E") to "google maps",
        Regex("${B}(?:việc ma lai|việc marbe|việc ma ray|viện mary|việc map|việt map|việt mắp|việt ma|viết map|viết mát|việt máp|việt mép|vi ét map)$E") to "vietmap",
        // THTVoice 13/11/2026 (owner test): "viên map li", "việc map lie", "vietmap lie". "map" + a "live"
        // sound only belongs to Vietmap Live, so it must never fall through to plain "map" (bản đồ).
        Regex("${B}(?:viên|viện|việc|việt|viết|viêt|viet|vi ét)(?: |-)?(?:map|máp|mép|mắp|mát|mạp)$E") to "vietmap",
        Regex("${B}vietmap (?:live|lai|lây|lie|li|lí|lại|lái|lay|lai vơ|lai phờ)$E") to "vietmap live",
        Regex("${B}(?:map|máp|mép|mắp) (?:live|lai|lây|lie|li|lí|lai vơ)$E") to "vietmap live",
        Regex("^mở (?:map|maps|mát|máy|mắp|mắt|bản đồ)$") to "mở bản đồ",
        Regex("^(?:dẫn đường|chỉ đường)(?: bằng)? vietmap$") to "mở vietmap",
        Regex("^(?:nghe|xem) youtube$") to "mở youtube",
        // Drive modes (01/10/2026): English mode names heard as Vietnamese syllables. Only after
        // "chế độ (lái)" / "chuyển (sang)" / "đổi sang", so "phanh sang …" and other uses stay intact.
        Regex("$B$DRIVE_LEAD (?:sờ pót|xờ pót|xì pót|sì pót|sờ pọt|xì pọt|spot|pót)$E$NOT_HVAC") to "chế độ lái sport",
        Regex("$B$DRIVE_LEAD (?:ê cô|e cô|i cô|ê có|e có|ê co|e co|i co|ê kô)$E$NOT_HVAC") to "chế độ lái eco",
        Regex("$B$DRIVE_LEAD (?:com pho|com phọt|com phợt|com phớt|căm pho|căm phợt|căm phọt|cam phọt|cam phợt|con phớt|con pho|cum phợt)$E$NOT_HVAC") to "chế độ lái comfort"
    )

    private fun fixOwnerReportedAsrSlips(input: String): String {
        var value = input
        ownerAsrSlips.forEach { (pattern, replacement) -> value = pattern.replace(value, replacement) }
        nearSoundSlips.forEach { (pattern, replacement) -> value = pattern.replace(value, replacement) }
        regionalSpeech.forEach { (pattern, replacement) -> value = pattern.replace(value, replacement) }
        // Shared scene corrections open an existing OEM screen; never rewrite navigation text.
        value = when {
            sharedCampingSlip.matches(value) -> "chế độ cắm trại"
            sharedRestSlip.matches(value) -> "chế độ nghỉ ngơi"
            else -> value
        }
        return value.trim()
    }

    /**
     * Near-sound ASR slips (09/10/2026): wrong tone or a close syllable on the command words
     * ("bặt điều hoa", "tăng nhiệt đổ", "mở nhạt", "bật đen viền"). Each rule is tied to its
     * command context so ordinary words elsewhere are left alone.
     */
    private val nearSoundSlips: List<Pair<Regex, String>> = listOf(
        // Verbs at the start of a command.
        Regex("^(?:bặt|bất) ") to "bật ",
        Regex("^(?:tất|tát|tặt) ") to "tắt ",
        Regex("^(?:mỡ|mớ|mơ|mờ|mợ) ") to "mở ",
        Regex("^(?:dảm|giám) ") to "giảm ",
        Regex("^(?:đông|đống) (?=(?:hết |tất cả |cả )?(?:kính|cửa|cốp))") to "đóng ",
        Regex("^xấy (?=kính|kín|gương|cánh)") to "sấy ",
        // Nouns of the car.
        Regex("${B}điều (?:hoa|khoa|hào|hoà)$E") to "điều hòa",
        Regex("${B}nhiệt (?:đổ|đồ|đô)$E") to "nhiệt độ",
        Regex("(?<=(?:tăng|giảm|bật|tắt|mở|chỉnh) )(?:quạ|quát)$E") to "quạt",
        Regex("(?<=(?:mở|tắt|bật|dừng|phát|nghe) )(?:nhạt|nhặc)$E") to "nhạc",
        Regex("${B}(?:đèn|đen) (?:viên|viện)$E") to "đèn viền",
        Regex("${B}đen viền$E") to "đèn viền",
        Regex("(?<=sấy )kín$E") to "kính",
        Regex("${B}lấy (?:dó|giò|dỏ)$E") to "lấy gió",
        Regex("${B}gió chong$E") to "gió trong",
        Regex("${B}(?:bảng|bản) (?:độ|đổ)$E") to "bản đồ",
        Regex("${B}bảng đồ$E") to "bản đồ",
        Regex("${B}âm (?:lượn|lương|lựng)$E") to "âm lượng",
        Regex("^(?:bày|bài) tiếp$") to "bài tiếp theo",
        Regex("^bày tiếp theo$") to "bài tiếp theo",
        // Shared THTVoice ASR regressions (424a7b9, 22/10/2026), restricted to car contexts.
        Regex("^(tăng|giảm|bật|tắt|chỉnh) quảng(?=$FAN_ASR_TAIL)") to "$1 quạt",
        Regex("^xây (kinh|kính|kín|gương)(?=(?: (?:trước|sau|lái|phụ|bên lái|bên phụ))?[.!?,]*$)") to "sấy $1",
        Regex("(?<=^sấy )kinh$E") to "kính",
        Regex("^(?:mở )?khe (?=kính|kiếng|cửa kính)") to "hé ",
        Regex("^chế độ(?: lái)? sơn$") to "chế độ lái sport"
    )

    /**
     * Regional speech (08/10/2026): words and endings drivers from the South, Centre and North use,
     * or the ASR writes for their accent. Word-bounded and mostly at the start/end of the command.
     */
    private val regionalSpeech: List<Pair<Regex, String>> = listOf(
        // Southern words.
        Regex("${B}kiếng$E") to "kính",
        Regex("${B}tui$E") to "tôi",
        Regex("${B}dô$E") to "vào",
        Regex("^xài ") to "mở ",
        // Final consonant swaps (t/c) heard in Southern/Central speech.
        Regex("^tắc ") to "tắt ",
        Regex("^bậc ") to "bật ",
        // Fan final-consonant variants are handled by the command-scoped regionalFan below.
        // Northern n/l: "kéo kính nên".
        Regex("${B}(kính|gió|quạt|nhiệt độ|âm lượng|tiếng) nên$") to "$1 lên",
        // Requests: "cho tôi …", "mở giùm cái …", "… giùm tôi".
        Regex("^cho (?:tôi|mình|em) (?=\\d)") to "",
        Regex("^(mở|bật|tắt|đóng|hạ|kéo|tăng|giảm|chỉnh) (?:giùm|dùm|giúp)(?: (?:tôi|mình|em))?(?: cái)? ") to "$1 ",
        Regex(" (?:giùm|dùm|giúp)(?: (?:tôi|mình|em|anh|chị))?$") to "",
        // "hông/hổng" = "không" at the end of a question ("đèn viền có bật hông").
        Regex("${B}(?:hông|hổng)$") to "không",
        // Ending particles (nha, nhen, nghen, hỉ, nè, với, vậy, hen…), possibly stacked.
        Regex("(?: (?:nha|nhá|nhen|nghen|nghe|hen|hỉ|nè|với|vậy|hả|nhé|đi mà|đi|thử coi|coi|liền|lẹ lên|lẹ|nhanh lên|luôn|cái))+$") to "",
        // Window wording without a named window.
        Regex("^(?:hạ|kéo|mở) (?:cửa )?kính xuống hết$") to "hạ hết kính",
        // Owner decision 14/10/2026: no window named → ask (WindowClarification); "hết" = all.
        Regex("^(?:kéo|nâng|đóng) (?:cửa )?kính lên hết$") to "đóng hết kính",
        Regex("^(mở|đóng) (?:cửa )?kính hết$") to "$1 hết kính",
        // Air direction: "gió vào chân" means feet.
        Regex("${B}vào chân$E") to "xuống chân"
    ) + regionalCommandSpeech() + newWordSlips()

    /**
     * 15/10/2026 (owner): "cốp" heard as "cốt", plus near-sound / regional slips for the newest
     * command words (trunk, window answers, volume by number). Each rule is tied to its command
     * context; NewWordSlipsTest checks each one and that other sentences stay unchanged.
     */
    private fun newWordSlips(): List<Pair<Regex, String>> = listOf(
        // Trunk: cốt/cốc/cóp/cộp/kốp… after a trunk verb, or before trước/sau/hậu.
        Regex("(?<=^(?:mở|đóng|bật|khóa|khoá|sập|mở khóa|mở khoá)(?: cái)? )$TRUNK_SLIP(?=(?: xe)?(?: (?:trước|sau|hậu|lại))?$)") to "cốp",
        Regex("${B}$TRUNK_SLIP(?= (?:xe )?(?:trước|sau|hậu|$FRONT_SLIP|$REAR_SLIP)$)") to "cốp",
        Regex("^$TRUNK_SLIP (?:xe )?(?=(?:trước|sau|hậu|$FRONT_SLIP|$REAR_SLIP)$)") to "cốp ",
        Regex("${B}cốp (?:xe )?hậu$E") to "cốp sau",
        // "trước" / "sau" after cốp or kính: Northern/Central tr→ch, Southern s→x.
        Regex("(?<=(?:cốp|kính|bên|phía|đằng) )$FRONT_SLIP$") to "trước",
        Regex("(?<=(?:cốp|kính|bên|phía|đằng) )$REAR_SLIP$") to "sau",
        // Window noun after a window verb: "mở kinh", "đóng kín", "hạ kiến".
        Regex("(?<=^(?:mở|đóng|hạ|nâng|kéo|quay) )(?:kinh|kín|kiến|kím|kiên)(?=(?: (?:lên|xuống|ra|lại|hết|lái|phụ|sau|trước|tài|bên .+|\\d+ phần trăm))?$)") to "kính",
        // Volume words.
        Regex("${B}(?:âm|ăm|am|ơm) (?:lượn|lương|lựng|lường|lưỡng|lượm|lượt|lưởng|luợng|lượng)$E") to "âm lượng",
        Regex("${B}(?:vô lum|vo lum|vô lium|vô liu|vôn lum|vô lùm|vồ lum|vô lúm)$E") to "volume",
        Regex("(?<=^(?:chỉnh|vặn|tăng|giảm|hạ|để|cho|đặt|bật|mở) )(?:lòa|loà|lo a|noa|nòa|lóa)$E") to "loa",
        Regex("(?<=\\d )(?:nất|nắc|nấp|nác|lấc|nấc)$E") to "nấc",
        Regex("(?<=\\d )(?:mứt|mực|mứk)$E") to "mức",
        Regex("(?<=(?:âm lượng|loa|volume|quạt|gió) )(?:mứt|mực|mứk)(?= \\d)") to "mức",
        Regex("${B}(?:phầm|phần|phân|phằn|phẩn) (?:trăm|chăm|tram|trâm|chăng|trầm)$E") to "phần trăm",
        Regex("(?<=(?:âm lượng|loa|volume) )(?:tối da|tối đà|tối đả|tối đá|tồi đa|tối ta)$") to "tối đa",
        // Southern "nhứt" = "nhất" in "lớn nhứt", "nhỏ nhứt", "cao nhứt"…
        Regex("(?<=(?:lớn|nhỏ|to|bé|cao|thấp|mạnh|yếu) )nhứt$E") to "nhất"
    )

    private const val TRUNK_SLIP = "(?:cốt|cốc|cóp|cộp|cọp|kốp|cớp|côp|cót|cọt|cộc|gốp|cốp)"
    private const val FRONT_SLIP = "(?:chước|trướt|trức|chướt|trưới|chứơc)"
    private const val REAR_SLIP = "(?:xau|sao|xao|sau)"

    /**
     * 12/10/2026: more Northern / Central / Southern wording and ASR slips found by probing the
     * parser. Whole-utterance (^…$) unless the words cannot appear elsewhere, so they cannot leak
     * into other commands; RegionalCommandSpeechTest checks each one and the unchanged ones.
     */
    private fun regionalCommandSpeech(): List<Pair<Regex, String>> = listOf(
        // Temperature with a verb but without "nhiệt độ": "để 24 độ", "chỉnh xuống 20 độ", "lên 26 độ".
        Regex("^(?:để|cho|chỉnh|đặt|đổi|chỉnh lại|đặt lại)(?: (?:lên|xuống|về|còn|ở|thành|sang))? (1[6-9]|2\\d|3[0-2]) độ(?: c)?$") to "nhiệt độ $1",
        // "giảm 20 độ" (no lên/xuống/còn/về) stays unparsed: it could mean "by 20".
        Regex("^(?:(?:tăng|giảm|hạ|nâng|chỉnh) (?:lên|xuống|về|còn)|lên|xuống) (1[6-9]|2\\d|3[0-2]) độ(?: c)?$") to "nhiệt độ $1",
        // Relative: "giảm 2 độ", "tăng lên 1 độ", "hạ bớt 3 độ".
        Regex("^(tăng|giảm|hạ|nâng)(?: (?:lên|xuống|bớt|thêm))? ([1-5]) độ$") to "$1 nhiệt độ $2 độ",
        // Feeling: "lạnh quá" → warmer, "nóng quá" → cooler (whole utterance only).
        Regex("^(?:trời |xe |trong xe |thấy |hơi |tôi thấy |mình thấy |em thấy )?(?:lạnh|rét|buốt|lạnh run|lạnh cóng)(?: (?:quá|quá trời|quá xá|quá chừng|ghê|ghê quá|lắm|dữ|dữ vậy|thật|thiệt|ác|à|á|trời ơi))+$") to "tăng nhiệt độ",
        Regex("^(?:trời |xe |trong xe |thấy |hơi |tôi thấy |mình thấy |em thấy )?(?:nóng|nực|oi|bức|oi bức|nóng bức|nóng nực|hầm|ngột)(?: (?:quá|quá trời|quá xá|quá chừng|ghê|ghê quá|lắm|dữ|dữ vậy|thật|thiệt|ác|à|á|trời ơi))+$") to "giảm nhiệt độ",
        // Fan: "quạt yếu lại", "gió nhẹ đi", "gió cấp 3", "gió nấc 3".
        Regex("^(?:cho )?(?:quạt|gió) (?:yếu|nhẹ|êm|nhỏ|bé)(?: (?:lại|xuống|bớt|hơn|chút|tí))?$") to "giảm quạt",
        Regex("^(?:cho )?(?:quạt|gió) (?:mạnh|to|lớn|bự)(?: (?:lên|thêm|hơn|chút|tí))?$") to "tăng quạt",
        Regex("${B}gió (?:cấp|nấc|bậc)$E") to "mức gió",
        // Windows: Southern "quay kính / quay kiếng" = wind the window.
        Regex("^quay (?=(?:cửa )?(?:kính|kiếng))") to "kéo ",
        // Music: "mở bài hát", "bật nhạc lên", "mở nhạc chơi".
        Regex("^(?:mở|bật|phát|cho nghe) (?:bài hát|bài nhạc|ca nhạc|nhạc nghe|nhạc lên|nhạc chơi|nhạc nghe chơi|chút nhạc|tí nhạc)$") to "mở nhạc",
        Regex("^(?:tạm ngưng|tạm ngừng|ngừng)(?= |$)") to "tạm dừng",
        Regex("^(?:bài kế|qua bài|qua bài khác|bài khác|đổi bài|đổi bài khác|nhảy bài|sang bài|sang bài khác|next|nét)$") to "bài tiếp theo",
        Regex("^(?:lùi bài|lui bài|lùi lại bài trước|bài hồi nãy|bài lúc nãy|bài khi nãy|trở lại bài trước|bài vừa rồi)$") to "bài trước",
        // Volume: "vặn nhỏ", "tiếng nhỏ lại", "âm lượng lớn lên", "loa to lên".
        Regex("^vặn (?:nhỏ|bé|nhẹ)(?: (?:lại|xuống|bớt|tiếng|loa))?$") to "giảm âm lượng",
        Regex("^vặn (?:to|lớn|bự)(?: (?:lên|tiếng|loa))?$") to "tăng âm lượng",
        Regex("^(?:tiếng|âm lượng|loa|nhạc|âm thanh) (?:nhỏ|bé|nhẹ) (?:lại|xuống|bớt|đi)$|^(?:nhỏ|bé) (?:tiếng|loa) (?:lại|xuống)$") to "giảm âm lượng",
        Regex("^(?:tiếng|âm lượng|loa|nhạc|âm thanh) (?:to|lớn|bự) (?:lên|thêm|ra)$|^(?:to|lớn) (?:tiếng|loa) lên$") to "tăng âm lượng",
        // Questions: Southern "nay" = today; bare "thứ mấy"; odometer vs range.
        Regex("^nay (?=(?:thứ|ngày|mùng|mồng) )") to "hôm nay ",
        Regex("^(thứ mấy|ngày mấy)(?: (?:rồi|hôm nay))?$") to "hôm nay $1",
        Regex("^(?:xe )?(?:đã )?(?:chạy|đi) (?:được )?bao nhiêu (?:km|cây số|cây|ki lô mét|kí lô mét) rồi$") to "xe đã đi bao nhiêu km",
        Regex("^(?:xe )?(?:còn )?(?:chạy|đi) (?:được )?(?:bao xa|bao nhiêu (?:km|cây số|cây|ki lô mét|kí lô mét))(?: nữa)$") to "còn đi được bao nhiêu km",
        Regex("^phạm vi (?:còn lại|di chuyển|hoạt động|còn)$") to "quãng đường còn lại"
    )

    private val NUM_WORD = "(?:không|một|mốt|hai|ba|bốn|tư|năm|lăm|sáu|bảy|tám|chín)"
    private val DIGITS = mapOf(
        "không" to 0, "một" to 1, "mốt" to 1, "hai" to 2, "ba" to 3, "bốn" to 4, "tư" to 4,
        "năm" to 5, "lăm" to 5, "sáu" to 6, "bảy" to 7, "tám" to 8, "chín" to 9
    )
    private val RELATIVE_VERB_BEFORE = Regex("(?:tăng|giảm|hạ|nâng|bớt|thêm) $")

    /**
     * Spoken two-digit shortcuts before the number parser: Southern "hăm hai" = 22, "hai lăm" = 25
     * (was read as 2 + 5 = 7), "hai mốt" = 21, "hai mươi mốt" = 21, and in a climate sentence or
     * before "độ" "hai sáu" = 26 (only when the result is a 16..32 °C setpoint, and never right
     * after tăng/giảm, where "giảm hai ba độ" means "by two or three degrees").
     */
    private fun colloquialNumbers(input: String): String {
        var v = input
        v = Regex("${B}hăm(?=(?: $NUM_WORD| độ)$E|$)").replace(v, "hai mươi")
        v = Regex("${B}mươi mốt$E").replace(v, "mươi một")
        v = Regex("${B}(một|hai|ba) (lăm|mốt)$E").replace(v) { m -> "${m.groupValues[1]} mươi ${if (m.groupValues[2] == "mốt") "một" else "lăm"}" }
        // "giảm hai ba độ" = by two or three degrees: keep the smaller step (was 2 + 3 = 5).
        v = Regex("(?<=(?:tăng|giảm|hạ|nâng|bớt|thêm)(?: (?:nhiệt độ|lên|xuống|bớt|thêm))? )(một|hai|ba|bốn) (hai|ba|bốn|năm)(?= độ$E)").replace(v) { m ->
            val a = DIGITS[m.groupValues[1]] ?: 0
            val b = DIGITS[m.groupValues[2]] ?: 0
            if (b == a + 1) m.groupValues[1] else m.value
        }
        val climate = Regex("nhiệt độ|điều hòa|điều hoà|máy lạnh").containsMatchIn(v)
        v = Regex("${B}(một|hai|ba) ($NUM_WORD)(?=( độ)?$E)").replace(v) { m ->
            val tens = DIGITS[m.groupValues[1]] ?: return@replace m.value
            val unit = DIGITS[m.groupValues[2]] ?: return@replace m.value
            val value = tens * 10 + unit
            val beforeDegree = m.groupValues[3].isNotEmpty()
            val prefix = v.substring(0, m.range.first)
            if ((climate || beforeDegree) && value in 16..32 && !RELATIVE_VERB_BEFORE.containsMatchIn(prefix)) value.toString() else m.value
        }
        return v
    }

    private const val WINDOW_NOUN = "(?:(?:cả )?(?:2 )?kính .+)"
    private val pullWindowUp = Regex("^(?:kéo|cho)(?: giúp tôi)? ($WINDOW_NOUN) lên$")
    private val pullWindowDown = Regex("^(?:kéo|cho)(?: giúp tôi)? ($WINDOW_NOUN) xuống$")
    private val raiseWindowFirst = Regex("^lên ($WINDOW_NOUN)$")
    private val lowerWindowFirst = Regex("^xuống ($WINDOW_NOUN)$")
    // ASR hears "kính" as "kênh"; only rewrite when a window position follows.
    private val windowChannelSlip = Regex("(?<![\\p{L}\\p{N}])kênh (sau|trước|phụ|lái|tài|ghế|phía|trái|phải|bên)(?![\\p{L}\\p{N}])")
    // ASR hears "kính" as "kín" ("đóng kín lái") or doubles it ("kín kênh lái").
    private val windowTightSlip = Regex("(?<![\\p{L}\\p{N}])kín (?:kênh |kính )?(lái|tài|phụ|ghế|trái|phải|sau|trước|bên)(?![\\p{L}\\p{N}])")

    /** Runs before the replacement table so "kênh lái" still becomes "kính bên lái". */
    private fun fixWindowNounSlips(input: String): String {
        var value = windowNoun.replace(input, "kính")
        value = windowTightSlip.replace(value) { "kính ${it.groupValues[1]}" }
        value = windowChannelSlip.replace(value) { "kính ${it.groupValues[1]}" }
        if (Regex("kính|kiếng|cửa sổ").containsMatchIn(value)) {
            value = value.replace(Regex("(?<=bên )lá$E"), "lái")
        }
        return value.replace("các kính", "kính").replace("các kiếng", "kiếng")
    }
    private val windowNoun = Regex("(?<![\\p{L}\\p{N}])cửa (?:sổ|xổ)(?! trời| tự do)(?![\\p{L}\\p{N}])")

    // Unified regional word corrections: always enabled, with no separate accent profile.
    private val regionalWindowLead = Regex("^(?:mở|đóng|hạ|nâng|kéo|quay|hé|dừng)(?: (?:lên|xuống))? (?:(?:tất cả|toàn bộ|hết|cả|các|[1-4]) )*(?:cửa kính|cửa sổ|kính|kiếng|cánh)(?: |$)")
    private val regionalDemistVerb = Regex("^(?:(bật|mở|tắt|ngắt) )?(?:sai|sái|sáy)(?= (?:kính|kiếng|gương)(?: |$))")
    private val regionalDemistLead = Regex("^(?:(?:bật|mở|tắt|ngắt) )?sấy (?:kính|kiếng|gương)(?: |$)")
    private val regionalCirculation = Regex("^(?:(?:bật|mở|chuyển(?: sang)?|đổi(?: sang)?|chỉnh|chọn|để) )?(?:chế độ )?(?:lấy )?(?:gió|khí) (treo|chong|trông|ngoai|ngoải|ngoãi|ngòai)$")
    private val regionalRows = listOf(
        Regex("${B}(?:seo|xau|xao|sao)$E") to "sau",
        Regex("${B}(?:chước|trướt|trức|chướt|trưới)$E") to "trước"
    )
    private const val REGIONAL_PASSENGER = "(?:phụ|phủ|phú|phù|phũ|fụ|fu|phu|bụ|vụ)"
    private val regionalWindowSides = listOf(
        // Long passenger names first; "tay phụ" must never turn into driver+passenger.
        Regex("${B}(?:tài|tay) $REGIONAL_PASSENGER$E") to "phụ",
        Regex("${B}ghé$E") to "ghế",
        Regex("(?<=(?:bên|ghế) )(?:tay|nái|lài|lãi|lải|lại|lấy|nãi)$E") to "lái",
        // A bare "lại" still means closing/again, never an inferred driver window.
        Regex("(?<=(?:kính|kiếng|cánh|cửa sổ|sau|trước) )(?:tay|nái|lài|lãi|lải|nãi)$E") to "lái",
        Regex("(?<=(?:bên|ghế|kính|kiếng|cánh|sau|trước) )$REGIONAL_PASSENGER$E") to "phụ",
        Regex("(?<=(?:bên|ghế|kính|kiếng|cánh|sau|trước) )(?:chái|chải|trãi|trải|chại)$E") to "trái",
        Regex("(?<=(?:bên|ghế|kính|kiếng|cánh|sau|trước) )(?:phãi|phái|fải|phai|phại)$E") to "phải"
    )
    private val regionalFan = Regex("^((?:(?:tăng|giảm|hạ|nâng|chỉnh|đặt|để|bật|tắt|mở)(?: tốc độ)? )?)(?:quạc|quặc)(?=$FAN_ASR_TAIL)")

    private fun normalizeRegionalVehicleWords(input: String): String {
        regionalCirculation.matchEntire(input)?.let {
            return if (it.groupValues[1] in setOf("treo", "chong", "trông")) "lấy gió trong" else "lấy gió ngoài"
        }
        var value = regionalDemistVerb.replace(input) { match ->
            match.groupValues[1].takeIf { it.isNotEmpty() }?.let { "$it sấy" } ?: "sấy"
        }
        val window = regionalWindowLead.containsMatchIn(value)
        if (window || regionalDemistLead.containsMatchIn(value)) {
            regionalRows.forEach { (pattern, replacement) -> value = pattern.replace(value, replacement) }
        }
        if (window) regionalWindowSides.forEach { (pattern, replacement) -> value = pattern.replace(value, replacement) }
        return regionalFan.replace(value, "$1quạt")
    }
    // In window phrases "trên" / "dưới" mean the front / rear row.
    private val windowUpperRow = Regex("(kính[^0-9]*?)(?<![\\p{L}\\p{N}])trên(?![\\p{L}\\p{N}])")
    private val windowLowerRow = Regex("(kính[^0-9]*?)(?<![\\p{L}\\p{N}])dưới(?![\\p{L}\\p{N}])")
    // "hé kính" with no window named: every window opens a crack (10 %).
    private val crackAllWindows = Regex("^(?:mở )?hé kính(?: 1 chút| một chút| cho thoáng)?$|^kính hé (?:1|một) chút$")
    private val windowLeaf = Regex("^(mở|đóng|nâng|hạ)((?: cả)?(?: 2)?) cánh (trước|sau)(?: lên| xuống)?$")

    /**
     * Whole-utterance window phrasing: "kéo kính X lên" / "lên kính X" close, "kéo kính X
     * xuống" / "xuống kính X" open, and "cánh trước/sau" means the window pair.
     */
    private fun canonicalizeWindowVerbs(input: String): String {
        var value = windowChannelSlip.replace(input) { "kính ${it.groupValues[1]}" }
        if (value.contains("kính")) {
            value = windowUpperRow.replace(value) { "${it.groupValues[1]}trước" }
            value = windowLowerRow.replace(value) { "${it.groupValues[1]}sau" }
        }
        if (crackAllWindows.matches(value)) return "mở tất cả kính 10 phần trăm"
        pullWindowUp.matchEntire(value)?.let { return "đóng ${it.groupValues[1]}" }
        pullWindowDown.matchEntire(value)?.let { return "mở ${it.groupValues[1]}" }
        raiseWindowFirst.matchEntire(value)?.let { return "đóng ${it.groupValues[1]}" }
        lowerWindowFirst.matchEntire(value)?.let { return "mở ${it.groupValues[1]}" }
        windowLeaf.matchEntire(value)?.let { return "${it.groupValues[1]}${it.groupValues[2]} kính ${it.groupValues[3]}" }
        return value
    }

    /**
     * The voice UX intentionally accepts a bare cabin setpoint after wake-up
     * (for example "20", "hai mươi", or "20 độ"). Canonicalize only an
     * entire standalone 16..32°C utterance so unrelated numbers in media,
     * navigation, fan, and window commands cannot be stolen by HVAC parsing.
     * Explicit standalone HI/LO are treated the same way; substring matches
     * such as "hi mươi" are deliberately not accepted as endpoints.
     */
    private fun canonicalizeStandaloneClimateShorthand(value: String): String {
        standaloneClimateSetpoint.matchEntire(value)?.let { match ->
            return "nhiệt độ ${match.groupValues[1]}"
        }
        standaloneHalfSetpoint.matchEntire(value)?.let { match ->
            return "nhiệt độ ${match.groupValues[1]} rưỡi"
        }
        return when (value) {
            "hi" -> "nhiệt độ hi"
            "lo" -> "nhiệt độ lo"
            else -> value
        }
    }
}
