package com.ch.hammerscale.controller.presentation.dto

fun Double.format(
    digits: Int
) = "%.${digits}f".format(this)
