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

    @Test fun uuidIndexMatchesSchema() {
        val v3 = schema(3).getJSONArray("entities")
        val trails = (0 until v3.length()).map(v3::getJSONObject).first { it.getString("tableName") == "trails" }
        val index = trails.getJSONArray("indices").getJSONObject(0).getString("createSql").replace("\${TABLE_NAME}", "trails")
        assertEquals(normalize(index), normalize(Migrations.SQL_2_3.last()))
    }

    @Test fun meshTableMatchesSchema() {
        val v4 = schema(4).getJSONArray("entities")
        val mesh = (0 until v4.length()).map(v4::getJSONObject).first { it.getString("tableName") == "mesh_messages" }
        val expected = mutableListOf(normalize(mesh.getString("createSql").replace("\${TABLE_NAME}", "mesh_messages")))
        val indices = mesh.getJSONArray("indices")
        for (i in 0 until indices.length()) {
            expected += normalize(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", "mesh_messages"))
        }
        assertEquals(expected.sorted(), Migrations.SQL_3_4.map(::normalize).sorted())
    }

    @Test fun chatTableMatchesSchema() {
        val v5 = schema(5).getJSONArray("entities")
        val chat = (0 until v5.length()).map(v5::getJSONObject).first { it.getString("tableName") == "chat_messages" }
        val expected = mutableListOf(normalize(chat.getString("createSql").replace("\${TABLE_NAME}", "chat_messages")))
        val indices = chat.getJSONArray("indices")
        for (i in 0 until indices.length()) {
            expected += normalize(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", "chat_messages"))
        }
        assertEquals(expected.sorted(), Migrations.SQL_4_5.map(::normalize).sorted())
    }
}
