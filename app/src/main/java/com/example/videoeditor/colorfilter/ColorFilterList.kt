package com.example.videoeditor.colorfilter

import com.example.videoeditor.*

data class ColorFilterList (
    private val list: List<ColorFilterItem> = listOf(
//        ColorFilterItem(
//            R.drawable.billy,
//            NO_FILTER
//        ),
        ColorFilterItem(
            R.drawable.billy_blue25,
            BLUE_FILTER
        ),
        ColorFilterItem(
            R.drawable.billy_green15,
            GREEN_FILTER
        ),
        ColorFilterItem(
            R.drawable.billy_red2,
            RED_FILTER
        )
    )
) {
    fun getList(): List<ColorFilterItem> = list
}

