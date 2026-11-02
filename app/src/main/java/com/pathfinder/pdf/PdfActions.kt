package com.pathfinder.pdf

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/** Imprimir, compartilhar e salvar a ficha gerada. */
object PdfActions {

    /** Pasta (em cache) onde as fichas são geradas; exposta pelo FileProvider. */
    fun outputDir(context: Context) = File(context.cacheDir, "pdf")

    /** Nome de arquivo seguro a partir do nome da trilha. */
    fun fileName(trailName: String): String {
        val base = trailName.trim().replace(Regex("[^\\p{L}\\p{N}_ -]"), "").replace(' ', '_').take(60)
        return "ficha_${base.ifEmpty { "trilha" }}.pdf"
    }

    fun print(context: Context, file: File, jobName: String) {
        val manager = context.getSystemService(PrintManager::class.java)
        manager.print(jobName, FilePrintAdapter(file, jobName), PrintAttributes.Builder().build())
    }

    /** Envia por WhatsApp, e-mail etc. */
    fun share(context: Context, file: File, title: String): Boolean {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            context.startActivity(Intent.createChooser(send, "Compartilhar ficha"))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /** Copia a ficha para um local escolhido pelo usuário (Storage Access Framework). */
    fun saveTo(context: Context, file: File, destination: Uri) {
        context.contentResolver.openOutputStream(destination)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        } ?: error("Não foi possível abrir o destino")
    }

    private class FilePrintAdapter(private val file: File, private val name: String) : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?,
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder(name)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build()
            callback.onLayoutFinished(info, oldAttributes != newAttributes)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback,
        ) {
            try {
                file.inputStream().use { input ->
                    FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) }
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }
    }
}
