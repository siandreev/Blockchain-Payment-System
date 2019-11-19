package dsx.bps.config.currencies

import com.uchuhimo.konf.ConfigSpec

object BtcConfig: ConfigSpec("btcConfig") {
    object Coin: ConfigSpec("coin") {
        val user by required<String>()
        val password by required<String>()
        val host by required<String>()
        val port by required<String>()
        val confirmations by required<Int>()
    }
    object Explorer: ConfigSpec("explorer") {
        val frequency by required<Long>()
    }
}