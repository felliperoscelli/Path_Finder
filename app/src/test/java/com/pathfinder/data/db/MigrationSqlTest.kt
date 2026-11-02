package com.pathfinder.data.db

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Garante que o SQL da migração 1→2 cria exatamente as tabelas e índices que
 * o Room espera na versão 2 (comparando com o esquema exportado em app/schemas).
 */
class MigrationSqlTest {
    private fun schema(version: Int): JSONObject {
        val file = File("schemas/com.pathfinder.data.db.AppDatabase/$version.json")
        return JSONObject(file.readText()).getJSONObject("database")
    }

    private fun normalize(sql: String) = sql.replace(Regex("\\s+"), " ").trim()

    @Test fun newTablesAndIndicesMatchSchema() {
        val v2 = schema(2).getJSONArray("entities")
        val expected = mutableListOf<String>()
        for (i in 0 until v2.length()) {
            val e = v2.getJSONObject(i)
            val table = e.getString("tableName")
            if (table in listOf("navigation_sessions", "deviation_events")) {
                expected += normalize(e.getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val indices = e.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                val sql = indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table)
                if ("sessionId" in sql || table in listOf("navigation_sessions", "deviation_events")) {
                    expected += normalize(sql)
                }
            }
        }
        val migration = Migrations.SQL_1_2.map(::normalize).filterNot { it.startsWith("ALTER") }
        assertEquals(expected.sorted(), migration.sorted())
    }
}
