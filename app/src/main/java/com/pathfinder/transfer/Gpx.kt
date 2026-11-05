package com.pathfinder.transfer

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.Locale
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory

/**
 * GPX 1.1, para abrir a trilha em outros apps (Google Earth, OsmAnd…) ou
 * importar trilhas feitas neles. Os pontos vão como waypoints e como rota
 * (na ordem); o percurso gravado vai como track.
 */
object Gpx {

    fun write(pkg: TrailPackage): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<gpx version="1.1" creator="Path Finder" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        append("  <metadata><name>").append(esc(pkg.name)).append("</name>")
        if (pkg.notes.isNotBlank()) append("<desc>").append(esc(pkg.notes)).append("</desc>")
        append("<time>").append(Instant.ofEpochMilli(pkg.createdAt)).append("</time></metadata>\n")
        pkg.points.forEach { append("  ").append(point("wpt", it)).append('\n') }
        append("  <rte><name>").append(esc(pkg.name)).append("</name>\n")
        pkg.points.forEach { append("    ").append(point("rtept", it)).append('\n') }
        append("  </rte>\n")
        if (pkg.track.isNotEmpty()) {
            append("  <trk><name>Percurso gravado</name><trkseg>\n")
            pkg.track.forEach { t ->
                append("    <trkpt ").append(latLon(t.latitude, t.longitude)).append('>')
                t.altitude?.let { append("<ele>").append(num(it, 1)).append("</ele>") }
                append("<time>").append(Instant.ofEpochMilli(t.timestamp)).append("</time></trkpt>\n")
            }
            append("  </trkseg></trk>\n")
        }
        append("</gpx>\n")
    }

    /**
     * Lê um GPX. Os pontos vêm da primeira rota; se não houver rota, dos
     * waypoints. O primeiro track vira o percurso gravado. A trilha recebe um
     * uuid novo (GPX não tem identificador).
     */
    fun read(bytes: ByteArray, fallbackName: String, deviationDistanceMeters: Int, deviationTimeSeconds: Int): TrailPackage {
        val doc = try {
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                // Sem DTD/entidades externas (XXE).
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        } catch (e: Exception) {
            throw IllegalArgumentException("Arquivo GPX inválido", e)
        }
        val root = doc.documentElement
        require(root.tagName.substringAfter(':') == "gpx") { "Arquivo não é GPX" }

        val route = root.children("rte").firstOrNull()
        val pointElements = route?.children("rtept")?.takeIf { it.isNotEmpty() } ?: root.children("wpt")
        val points = pointElements.mapIndexed { i, e ->
            PackagePoint(
                name = e.childText("name") ?: "Ponto ${i + 1}",
                notes = e.childText("desc") ?: e.childText("cmt") ?: "",
                latitude = e.getAttribute("lat").toDouble(),
                longitude = e.getAttribute("lon").toDouble(),
                altitude = e.childText("ele")?.toDoubleOrNull(),
            )
        }
        val track = root.children("trk").firstOrNull()
            ?.children("trkseg")?.flatMap { it.children("trkpt") }
            ?.mapIndexed { i, e ->
                PackageTrackPoint(
                    latitude = e.getAttribute("lat").toDouble(),
                    longitude = e.getAttribute("lon").toDouble(),
                    altitude = e.childText("ele")?.toDoubleOrNull(),
                    timestamp = e.childText("time")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: i * 1000L,
                )
            }.orEmpty()
        require(points.isNotEmpty() || track.isNotEmpty()) { "O GPX não tem pontos" }

        val metadata = root.children("metadata").firstOrNull()
        val name = metadata?.childText("name") ?: route?.childText("name") ?: fallbackName
        return TrailPackage(
            uuid = UUID.randomUUID().toString(),
            name = name,
            notes = metadata?.childText("desc").orEmpty(),
            createdAt = metadata?.childText("time")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                ?: System.currentTimeMillis(),
            deviationDistanceMeters = deviationDistanceMeters,
            deviationTimeSeconds = deviationTimeSeconds,
            points = points,
            track = track,
        )
    }

    private fun point(tag: String, p: PackagePoint) = buildString {
        append('<').append(tag).append(' ').append(latLon(p.latitude, p.longitude)).append('>')
        p.altitude?.let { append("<ele>").append(num(it, 1)).append("</ele>") }
        append("<name>").append(esc(p.name)).append("</name>")
        if (p.notes.isNotBlank()) append("<desc>").append(esc(p.notes)).append("</desc>")
        append("</").append(tag).append('>')
    }

    private fun latLon(lat: Double, lon: Double) = """lat="${num(lat, 7)}" lon="${num(lon, 7)}""""

    private fun num(v: Double, decimals: Int) = String.format(Locale.US, "%.${decimals}f", v)

    private fun esc(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun Element.children(tag: String): List<Element> {
        val nodes = childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
            .filter { it.tagName.substringAfter(':') == tag }
    }

    private fun Element.childText(tag: String): String? =
        children(tag).firstOrNull()?.textContent?.trim()?.takeIf { it.isNotEmpty() }
}
