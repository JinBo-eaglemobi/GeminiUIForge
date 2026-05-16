package org.gemini.ui.forge.service

import androidx.compose.ui.text.intl.Locale
import org.gemini.ui.forge.utils.scaleImage
import kotlin.io.path.Path
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.test.Test

class Test {


    @Test
    fun test() {

        println(Locale.current.language)
        println(Locale.current.platformLocale)
        println(Locale.current.region)

    }

    @Test
    fun testCropImage() {

        val imagePath =
            Path("C:\\Users\\10371\\.geminiuiforge\\templates\\effect_main\\assets\\gift_icon/crop_1778840864765_1778840864765.png")
        val imageBytes = imagePath.readBytes()
        val byte2 = scaleImage(imageBytes, 60, 60)

        imagePath.parent.resolve("crop_tttt.png").writeBytes(byte2!!)

    }


}