package com.ch.hammerscale.control.presentation.dto

fun Double.format(digits: Int) = "%.${digits}f".format(this)
