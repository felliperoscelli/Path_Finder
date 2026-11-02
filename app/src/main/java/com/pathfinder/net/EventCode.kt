package com.pathfinder.net

/** Código do evento: isola a rede de grupos de outros eventos por perto. */
object EventCode {
    private const val BASE_SERVICE_ID = "com.pathfinder.mesh"
    const val MAX_LENGTH = 20

    /** Maiúsculas, só letras, números e hífen (ex.: "aguia 27" → "AGUIA27"). */
    fun normalize(code: String): String = code.uppercase()
        .replace(Regex("[ÁÀÂÃ]"), "A").replace(Regex("[ÉÊ]"), "E").replace("Í", "I")
        .replace(Regex("[ÓÔÕ]"), "O").replace("Ú", "U").replace("Ç", "C")
        .filter { it in 'A'..'Z' || it in '0'..'9' || it == '-' }
        .take(MAX_LENGTH)

    /** Aparelhos só se encontram na Nearby se o serviceId for igual. */
    fun serviceId(code: String): String = normalize(code).let { if (it.isEmpty()) BASE_SERVICE_ID else "$BASE_SERVICE_ID.$it" }
}
