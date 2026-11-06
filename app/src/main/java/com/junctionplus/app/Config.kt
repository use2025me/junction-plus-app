package com.junctionplus.app

object Config {
    /** The page the app opens on. */
    const val HOME_URL = "https://stream.junctionplus.com"

    /**
     * Websites that open inside the app. Anything else (e.g. social links)
     * opens in the phone's browser. Sub-domains are included automatically.
     * Uscreen and payment domains are here so sign-in and checkout stay in the app.
     */
    val INTERNAL_HOSTS = listOf(
        "junctionplus.com",
        "uscreen.io",
        "uscreen.tv",
        "uscreencdn.com",
        "stripe.com",
        "stripe.network",
        "paypal.com",
    )
}
