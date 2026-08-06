package com.marketpluss.widget

data class MarketItem(
    val name: String,
    val value: Double,
    val changePercent: Double,
    val unit: String,
    val formatDecimals: Int = 0
)

data class MarketSnapshot(
    val items: List<MarketItem>,
    val updatedAt: String
)
