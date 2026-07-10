package dev.accessscope.scanner.export.pdf

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Contesto interno per il layout e il disegno progressivo delle pagine PDF.
 *
 * Gestisce paginazione automatica, paint preconfigurati e utilità di disegno testo/rettangoli.
 *
 * @property document Documento PDF Android su cui scrivere le pagine.
 */
internal class PdfContext(private val document: PdfDocument) {
    /** Coordinata Y corrente sul canvas della pagina attiva. */
    var y = 40f
    private var pageNumber = 0
    private lateinit var page: PdfDocument.Page

    init {
        page = createPage()
    }

    /** Paint per il titolo principale (copertina). */
    val titlePaint = paint(26f, true)
    /** Paint per il sottotitolo. */
    val subtitlePaint = paint(14f, false)
    /** Paint per le intestazioni di sezione. */
    val headingPaint = paint(16f, true)
    /** Paint per i titoli di area/schermata. */
    val areaTitlePaint = paint(15f, true)
    /** Paint per il corpo del testo. */
    val bodyPaint = paint(11f, false)
    /** Paint per il corpo in grassetto. */
    val bodyBoldPaint = paint(11f, true)
    /** Paint per metadati e testo secondario. */
    val metaPaint = paint(9.5f, false)

    /**
     * Chiude la pagina corrente e ne apre una nuova, azzerando [y].
     */
    fun newPage() {
        document.finishPage(page)
        page = createPage()
        y = 40f
    }

    /**
     * Apre una nuova pagina se lo spazio verticale residuo è insufficiente.
     *
     * @param required Altezza minima richiesta in punti dalla posizione [y] corrente.
     */
    fun ensureSpace(required: Float) {
        if (y + required > PAGE_H - 40f) newPage()
    }

    /**
     * Chiude l'ultima pagina aperta senza crearne una nuova.
     */
    fun finish() {
        document.finishPage(page)
    }

    /**
     * Genera un nome file univoco basato sulla data e ora corrente.
     *
     * @return Nome file nel formato `AccessScope_yyyyMMdd_HHmmss.pdf`.
     */
    fun fileName() = "AccessScope_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.pdf"

    /**
     * Crea e avvia una nuova pagina PDF con dimensioni A4 in punti.
     *
     * @return Pagina appena creata e pronta per il disegno.
     */
    private fun createPage(): PdfDocument.Page {
        pageNumber++
        val info = PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), pageNumber).create()
        return document.startPage(info)
    }

    /**
     * Disegna una singola riga di testo sul canvas della pagina corrente.
     *
     * @param text Testo da disegnare.
     * @param x Coordinata X in punti.
     * @param yPos Coordinata Y della baseline in punti.
     * @param paint Paint con dimensione e stile del carattere.
     * @param color Colore ARGB del testo.
     */
    fun drawText(text: String, x: Float, yPos: Float, paint: Paint, color: Int) {
        paint.color = color
        page.canvas.drawText(text, x, yPos, paint)
    }

    /**
     * Disegna un rettangolo pieno sul canvas della pagina corrente.
     *
     * @param l Coordinata X dell'angolo superiore sinistro.
     * @param t Coordinata Y dell'angolo superiore sinistro.
     * @param w Larghezza in punti.
     * @param h Altezza in punti.
     * @param color Colore ARGB di riempimento.
     */
    fun fillRect(l: Float, t: Float, w: Float, h: Float, color: Int) {
        val p = Paint().apply { this.color = color }
        page.canvas.drawRect(RectF(l, t, l + w, t + h), p)
    }

    /**
     * Disegna un bitmap scalato sul canvas della pagina corrente.
     */
    fun drawBitmap(bitmap: Bitmap, x: Float, yPos: Float, width: Float, height: Float) {
        val dest = RectF(x, yPos, x + width, yPos + height)
        page.canvas.drawBitmap(bitmap, null, dest, null)
    }

    /**
     * Disegna testo con a capo automatico entro una larghezza massima.
     *
     * Aggiorna [y] alla fine del blocco disegnato.
     *
     * @param text Testo da disegnare, eventualmente su più righe.
     * @param x Coordinata X in punti.
     * @param startY Coordinata Y di partenza.
     * @param maxW Larghezza massima disponibile per il testo.
     * @param paint Paint per il rendering del testo.
     * @param color Colore ARGB del testo.
     */
    fun drawWrapped(text: String, x: Float, startY: Float, maxW: Float, paint: Paint, color: Int) {
        paint.color = color
        var cy = startY
        wrap(text, paint, maxW).forEach { line ->
            ensureSpace(16f)
            page.canvas.drawText(line, x, cy, paint)
            cy += 14f
        }
        y = cy
    }

    /**
     * Suddivide il testo in righe che rientrano nella larghezza massima misurata col [paint].
     *
     * @param text Testo da spezzare.
     * @param paint Paint usato per misurare la larghezza dei caratteri.
     * @param maxW Larghezza massima per riga in punti.
     * @return Elenco di righe pronte per il disegno.
     */
    private fun wrap(text: String, paint: Paint, maxW: Float): List<String> {
        val words = text.split(' ')
        val lines = mutableListOf<String>()
        var cur = StringBuilder()
        words.forEach { w ->
            val cand = if (cur.isEmpty()) w else "$cur $w"
            if (paint.measureText(cand) <= maxW) cur = StringBuilder(cand)
            else {
                if (cur.isNotEmpty()) lines += cur.toString()
                cur = StringBuilder(w)
            }
        }
        if (cur.isNotEmpty()) lines += cur.toString()
        return lines.ifEmpty { listOf(text) }
    }

    /**
     * Crea un [Paint] per il testo con dimensione e peso specificati.
     *
     * @param size Dimensione del testo in punti.
     * @param bold `true` per carattere grassetto.
     * @return Paint configurato con antialiasing.
     */
    private fun paint(size: Float, bold: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
}
