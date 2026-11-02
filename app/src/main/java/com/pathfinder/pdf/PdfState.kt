package com.pathfinder.pdf

import java.io.File

/** Geração de um PDF (ficha ou resultado) para imprimir, compartilhar ou salvar. */
sealed interface PdfState {
    data object Generating : PdfState
    data class Ready(val file: File, val title: String) : PdfState
    data class Failed(val message: String) : PdfState
}
