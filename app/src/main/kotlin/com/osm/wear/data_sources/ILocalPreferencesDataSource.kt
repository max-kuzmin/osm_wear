package com.osm.wear.data_sources

interface ILocalPreferencesDataSource {
    fun getString(key: String, defValue: String?): String?
    fun putString(key: String, value: String?)
    fun getInt(key: String, defValue: Int): Int
    fun putInt(key: String, value: Int)
    fun getFloat(key: String, defValue: Float): Float
    fun putFloat(key: String, value: Float)
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun remove(key: String)
}
