package dsx.bps.crypto.xrp

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.source.yaml
import dsx.bps.config.currencyconfig.XrpConfig
import dsx.bps.core.datamodel.*
import dsx.bps.core.datamodel.Currency
import dsx.bps.crypto.common.CoinClient
import dsx.bps.crypto.xrp.datamodel.*
import java.io.File
import java.math.BigDecimal
import kotlin.random.Random

class XrpClient: CoinClient {

    override val currency = Currency.XRP
    override val config: Config

    private val account: String
    private val privateKey: String
    private val passPhrase: String

    override val rpc: XrpRpc
    override val blockchainListener: XrpBlockchainListener

    constructor(){
        config = Config()

        account = "rHb9CJAWyB4rj91VRWn96DkukG4bwdtyTh"
        privateKey = "snoPBrXtMeMyMHUVTgbuqAfg1SUTb"
        passPhrase = "masterpassphrase"

        val url = "http://127.0.0.1:51234/"
        rpc = XrpRpc(url)

        blockchainListener = XrpBlockchainListener(this, 5000)
    }

    constructor(conf: Config){
        config = conf

        account = config[XrpConfig.account]
        privateKey = config[XrpConfig.privateKey]
        passPhrase = config[XrpConfig.passPhrase]

        val host = config[XrpConfig.host]
        val port = config[XrpConfig.port]
        val url = "http://$host:$port/"
        rpc = XrpRpc(url)

        val frequency = config[XrpConfig.frequency]
        blockchainListener = XrpBlockchainListener(this, frequency)
    }

    constructor(configPath: String){
        val initConfig = Config()
        val configFile = File(configPath)
        config = with (initConfig) {
            addSpec(XrpConfig)
            from.yaml.file(configFile)
        }

        config.validateRequired()

        account = config[XrpConfig.account]
        privateKey = config[XrpConfig.privateKey]
        passPhrase = config[XrpConfig.passPhrase]

        val host = config[XrpConfig.host]
        val port = config[XrpConfig.port]
        val url = "http://$host:$port/"
        rpc = XrpRpc(url)

        val frequency = config[XrpConfig.frequency]
        blockchainListener = XrpBlockchainListener(this, frequency)
    }

    constructor(xrpRpc: XrpRpc, xrpBlockchainListener: XrpBlockchainListener, configPath: String): super(){
        val initConfig = Config()
        val configFile = File(configPath)
        config = with (initConfig) {
            addSpec(XrpConfig)
            from.yaml.file(configFile)
        }

        config.validateRequired()

        account = config[XrpConfig.account]
        privateKey = config[XrpConfig.privateKey]
        passPhrase = config[XrpConfig.passPhrase]

        rpc = xrpRpc
        blockchainListener = xrpBlockchainListener
    }

    override fun getBalance(): BigDecimal = rpc.getBalance(account)

    override fun getAddress(): String = account

    override fun getTag(): Int? = Random.nextInt(0, Int.MAX_VALUE)

    override fun getTx(txid: TxId): Tx {
        val xrtTx = rpc.getTransaction(txid.hash)
        return constructTx(xrtTx)
    }

    override fun sendPayment(amount: BigDecimal, address: String, tag: Int?): Tx {
        return createTransaction(amount, address, tag)
            .let { rpc.sign(privateKey, it) }
            .let { rpc.submit(it) }
            .let { constructTx(it) }
    }

    private fun createTransaction(amount: BigDecimal, address: String, tag: Int?): XrpTxPayment {
        val fee = rpc.getTxCost()
        val seq = rpc.getSequence(account)
        return XrpTxPayment(account, amount, address, fee.toPlainString(), seq, tag)
    }

    fun getLastLedger(): XrpLedger = rpc.getLastLedger()

    fun getLedger(hash: String): XrpLedger = rpc.getLedger(hash)

    fun getAccountTxs(indexMin: Long, indexMax: Long): XrpAccountTxs = rpc.getAccountTxs(account, indexMin, indexMax)

    fun constructTx(xrpAccountTx: XrpAccountTx): Tx {
        val tx = xrpAccountTx.tx
        val delivered = xrpAccountTx.meta.deliveredAmount
        var amount = BigDecimal.ZERO
        if (delivered?.currency == currency.name)
            amount = delivered.value

        return object: Tx {

            override fun currency() = Currency.XRP

            override fun hash() = tx.hash

            override fun index() = tx.sequence

            override fun amount() = amount

            override fun tag() = tx.destinationTag

            override fun destination() = tx.destination

            override fun fee() = BigDecimal(tx.fee)

            override fun status() = if (xrpAccountTx.validated) TxStatus.CONFIRMED else TxStatus.VALIDATING
        }
    }

    fun constructTx(xrpTx: XrpTx): Tx {
        val delivered = xrpTx.meta?.deliveredAmount
        var amount = BigDecimal.ZERO
        if (delivered?.currency == currency.name)
            amount = delivered.value

        return object: Tx {

            override fun currency() = Currency.XRP

            override fun hash() = xrpTx.hash

            override fun index() = xrpTx.sequence

            override fun amount() = amount

            override fun destination() = xrpTx.destination

            override fun tag() = xrpTx.destinationTag

            override fun fee() = BigDecimal(xrpTx.fee)

            override fun status() = if (xrpTx.validated) TxStatus.CONFIRMED else TxStatus.VALIDATING
        }
    }
}