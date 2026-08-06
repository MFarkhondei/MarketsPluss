package com.marketpluss.widget

data class MarketItem(
    val name: String,
    val value: Double,
    val changePercent: Double,
    val unit: String,
    val formatDecimals: Int = 0,
    /** بیشترین قیمت (فیلد h منبع — بازه روزانه TGJU) */
    val high: Double = 0.0,
    /** کمترین قیمت (فیلد l منبع) */
    val low: Double = 0.0
)

data class MarketSnapshot(
    val items: List<MarketItem>,
    val updatedAt: String
)
