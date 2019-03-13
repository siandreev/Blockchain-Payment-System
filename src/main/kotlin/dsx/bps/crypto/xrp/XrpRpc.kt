package dsx.bps.crypto.xrp

import com.google.gson.JsonElement
import dsx.bps.crypto.xrp.datamodel.*
import dsx.bps.rpc.JsonRpcHttpClient
import java.io.InputStream
import java.math.BigDecimal

class XrpRpc(url: String): JsonRpcHttpClient(url) {

    fun getBalance(account: String): BigDecimal {
        val accountData = getAccountInfo(account)
        return BigDecimal(accountData.balance)
    }

    fun getLastLedger(tx: Boolean = false, validated: Boolean = true): XrpLedger {
        val params = mutableMapOf<String, Any>("transactions" to tx)
        if (validated)
            params["ledger_index"] = "validated"
        val result = query("ledger", params)
        val obj = gson.toJsonTree(result).asJsonObject
        return gson.fromJson(obj["ledger"], XrpLedger::class.java)
    }

    fun getLedger(ledgerHash: String, tx: Boolean = false, validated: Boolean = true): XrpLedger {
        val params = mutableMapOf("ledger_hash" to ledgerHash, "transactions" to tx)
        if (validated)
            params["ledger_index"] = "validated"
        val result = query("ledger", params)
        val obj = gson.toJsonTree(result).asJsonObject
        return gson.fromJson(obj["ledger"], XrpLedger::class.java)
    }

    fun getAccountInfo(account: String, validated: Boolean = true): XrpAccountData {
        val params = mutableMapOf("account" to account)
        if (validated) {
            params["ledger_index"] = "validated"
        }
        val result  = query("account_info", params)
        val obj = gson.toJsonTree(result).asJsonObject
        return gson.fromJson(obj["account_data"], XrpAccountData::class.java)
    }

    fun getServerInfo(): XrpServerInfo {
        val result = query("server_info")
        val obj = gson.toJsonTree(result).asJsonObject
        return gson.fromJson(obj["info"], XrpServerInfo::class.java)
    }

    fun getAccountTx(account: String, indexMin: Long, indexMax: Long): List<XrpTx> {
        val params =  mapOf(
            "account" to account,
            "ledger_index_min" to indexMin,
            "ledger_index_max" to indexMax
        )
        val result = query("account_tx", params)
        val obj = gson.toJsonTree(result).asJsonObject
        val txs = obj["transactions"].asJsonArray
        
        return txs.map { tx ->
            tx
                .let { tx.asJsonObject }
                .let { gson.fromJson(it["tx"], XrpTx::class.java)
                    .also { xrpTx -> xrpTx.validated = it["validated"].asBoolean }
                }
        }
    }

    fun sign(privateKey: String, tx: XrpTxPayment, offline: Boolean = true): String {
        return sign(privateKey, gson.toJsonTree(tx, XrpTxPayment::class.java), offline)
    }

    private fun sign(privateKey: String, txJson: JsonElement, offline: Boolean = true): String {
        // TODO: implement local tx sign
        val params = mutableMapOf(
            "secret" to privateKey,
            "tx_json" to txJson,
            "offline" to offline)
        val result = query("sign", params)
        val obj = gson.toJsonTree(result).asJsonObject
        return obj["tx_blob"].asString
    }

    fun submit(txBlob: String): String {
        val params = mapOf("tx_blob" to txBlob)
        val result = query("submit", params)
        val obj = gson.toJsonTree(result).asJsonObject

        if (obj["engine_result"].asString != "tesSUCCESS")
            throw RuntimeException("engine_result: ${obj["engine_result"].asString}\n" +
                    "engine_result_code: ${obj["engine_result_code"].asInt}\n" +
                    "engine_result_message: ${obj["engine_result_message"].asString}")

        val tx: XrpTx = gson.fromJson(obj["tx_json"], XrpTx::class.java)
        return tx.hash
    }

    fun getSequence(account: String): Long {
        val accountData = getAccountInfo(account)
        return accountData.sequence
    }

    fun getTxCost(): String {
        val info = getServerInfo()
        val loadFactor = BigDecimal(info.loadFactor)
        val baseFee = info.validatedLedger.baseFeeXrp
        val drops = (baseFee * loadFactor * BigDecimal(1e6)).longValueExact()
        return drops.toString()
    }

    fun ledgerAccept(): Long {
        val result = query("ledger_accept")
        val obj = gson.toJsonTree(result).asJsonObject
        return obj["ledger_current_index"].asLong
    }

    override fun parseResponse(input: InputStream, id: String): Any? {
        val r = input.use { it.readBytes().toString(charset) }

        val json = r
            .let { gson.fromJson(r, Map::class.java) }
            .let { gson.toJsonTree(it["result"]).asJsonObject }

        if (json["status"].asString == "error") {
            val code = json["error_code"].asInt
            val error = json["error"].asString
            val message = json["error_message"].asString
            val request = json["request"].asString
            throw RuntimeException("RPC error \"$error\": code $code, message: \"$message\", request: $request")
        }

        return json
    }
}