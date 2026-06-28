package com.osm.wear.data_sources

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.io.OutputStream

interface ILocalFileDataSource {
    fun getGpxDirectory(): File
    fun getRegionDirectory(): File
    fun openInputStream(uri: Uri): InputStream?
    fun openOutputStream(file: File): OutputStream
    fun getFileNameFromUri(uri: Uri): String?
}
