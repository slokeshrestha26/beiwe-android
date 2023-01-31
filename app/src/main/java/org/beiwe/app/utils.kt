package org.beiwe.app

import android.app.PendingIntent
import android.os.Build
import android.util.Log

// This file is a location for new static functions, further factoring into files will occur when length of file becomes a problem.

val APP_NAME = "Beiwe"

// our print function
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


fun pending_intent_flag_fix(flag: Int): Int {
    // pending intents require that they inglude FLAG_IMMUTABLE or FLAG_MUTABLE in API 30 (android 12) and above.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        return (PendingIntent.FLAG_IMMUTABLE or flag)
    else
        return flag
}