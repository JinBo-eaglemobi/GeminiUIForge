package org.gemini.ui.forge

import androidx.compose.ui.text.intl.Locale
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import org.gemini.ui.forge.data.TemplateFile
import org.gemini.ui.forge.utils.scaleImage
import kotlin.test.Test

class PathTest {



    @Test
    fun test() {

        println(Locale.current.language)
        println(Locale.current.region)

    }

    @Test
    fun testPath() {

        val path = TemplateFile.inAppDir("ggg")
        println(path.getAbsolutePath())

    }

    @Test
    fun testCropImage() {

        val imagePath =
            Path("C:\\Users\\10371\\.geminiuiforge\\templates\\effect_main\\assets\\gift_icon/crop_1778840864765_1778840864765.png")
        val imageBytes = SystemFileSystem.source(imagePath).buffered().readByteArray()
        val byte2 = scaleImage(imageBytes, 60, 60)


        SystemFileSystem.sink(imagePath.parent!!).buffered().write(byte2!!)

    }







}