package de.vincentsberger.iptvplayer.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR-Code als Matrix (ohne Rand – den setzt [QrCode] selbst). */
internal fun qrMatrix(text: String): BitMatrix =
    QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, 0, 0,
        mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
    )

/** QR-Code schwarz auf weiß mit ruhiger Zone – dunkle Hintergründe lesen Handy-Kameras schlecht. */
@Composable
fun QrCode(text: String, size: Dp, modifier: Modifier = Modifier) {
    val matrix = remember(text) { qrMatrix(text) }
    // Feste Größe: Wird es eng (z. B. Tastatur offen), wird der Code beschnitten statt verzerrt.
    Box(modifier.requiredSize(size + 20.dp).background(Color.White, SmallCorner).padding(10.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val cell = minOf(this.size.width, this.size.height) / matrix.width
            for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
                if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}
