package com.marketpluss.widget

data class MarketItem(
    val name: String,
    val value: Double,
    val changePercent: Double,
    val unit: String,
    val formatDecimals: Int = 0,
    val rsi14: Double? = null
)

data class MarketSnapshot(
    val items: List<MarketItem>,
    val updatedAt: String
)
