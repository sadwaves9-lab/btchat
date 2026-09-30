package com.example.btchat.ui.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * QR Generator — encodes MAC + device info as QR.
 *
 * Payload format: "btchat://pair?mac=XX:XX:XX&name=DeviceName&v=3"
 */
object QRGenerator {

    fun buildPairPayload(mac: String, name: String, version: Int = 3): String =
        "btchat://pair?mac=$mac&name=${java.net.URLEncoder.encode(name, "UTF-8")}&v=$version"

    fun generate(payload: String, size: Int = 720): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }
}
