package com.nutritareas.app

import android.app.Application
import com.nutritareas.app.di.AppContainer
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class NutriTareasApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Must run before any PDDocument use (PdfTextExtractor); see its class doc.
        PDFBoxResourceLoader.init(applicationContext)
        container = AppContainer(this)
    }
}
