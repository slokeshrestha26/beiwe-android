package org.beiwe.app

import android.util.Log

val APP_NAME = "Beiwe"

// I want a gorram print command.
fun printi(text: Any?) {
    Log.i(APP_NAME, "" + text)
}
fun printi(tag: String, text: Any?) {
    Log.i(tag, "" + text)
}
fun printe(text: Any?) {
    Log.e(APP_NAME, "" + text)
}
fun printe(tag: String, text: Any?) {
    Log.e(tag, "" + text)
}
fun printv(text: Any?) {
    Log.v(APP_NAME, "" + text)
}
fun printv(tag: String, text: Any?) {
    Log.v(tag, "" + text)
}
fun printw(text: Any?) {
    Log.w(APP_NAME, "" + text)
}
fun printw(tag: String, text: Any?) {
    Log.w(tag, "" + text)
}
fun printd(text: Any?) {
    Log.d(APP_NAME, "" + text)
}
fun printd(tag: String, text: Any?) {
    Log.d(tag, "" + text)
}
fun print(text: Any?) {
    printd(text)
}
fun print(tag: String, text: Any?) {
    printd(tag, text)
}
