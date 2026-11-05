package com.pathfinder.transfer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File

/** Compartilhar, salvar e abrir arquivos (fichas, trilhas exportadas). */
object FileActions {

    fun exportDir(context: Context) = File(context.cacheDir, "export")

    /** Nome de arquivo seguro a partir do nome da trilha. */
    fun safeName(name: String): String =
        name.trim().replace(Regex("[^\\p{L}\\p{N}_ -]"), "").replace(' ', '_').take(60).ifEmpty { "trilha" }

    /** Abre o seletor do Android (WhatsApp, e-mail, Drive…). */
    fun share(context: Context, file: File, mime: String, title: String): Boolean {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            context.startActivity(Intent.createChooser(send, title))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /** Copia o arquivo para um local escolhido pelo usuário. */
    fun saveTo(context: Context, file: File, destination: Uri) {
        context.contentResolver.openOutputStream(destination)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        } ?: error("Não foi possível abrir o destino")
    }

    /** Nome do arquivo escolhido (sem extensão), para usar como nome da trilha. */
    fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.substringBeforeLast('.') else null
        }
}
