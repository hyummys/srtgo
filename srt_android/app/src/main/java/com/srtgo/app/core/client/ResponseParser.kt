package com.srtgo.app.core.client

import com.srtgo.app.core.exception.*
import org.json.JSONArray
import org.json.JSONObject

object ResponseParser {

    fun parseSrtResponse(responseText: String): SrtParsedResponse {
        val json = JSONObject(responseText)

        if (json.has("ErrorCode") && json.has("ErrorMsg")) {
            throw SrtResponseException(
                "[${json.getString("ErrorCode")}]: ${json.getString("ErrorMsg")}"
            )
        }

        if (!json.has("resultMap")) {
            throw SrtResponseException("Unexpected response format")
        }

        val resultMap = json.getJSONArray("resultMap").getJSONObject(0)
        val strResult = resultMap.optString("strResult", "")
        val msgTxt = resultMap.optString("msgTxt", "")

        return SrtParsedResponse(
            success = strResult == "SUCC",
            message = msgTxt,
            json = json
        )
    }

    fun checkKtxResult(json: JSONObject) {
        val strResult = json.optString("strResult", "")
        if (strResult == "FAIL") {
            val msgCode = json.optString("h_msg_cd", "")
            val msgTxt = json.optString("h_msg_txt", "")

            when {
                msgCode in KtxNeedLoginException.CODES -> throw KtxNeedLoginException(msgCode)
                msgCode in KtxNoResultsException.CODES -> throw KtxNoResultsException(msgCode)
                msgCode in KtxSoldOutException.CODES -> throw KtxSoldOutException(msgCode)
                else -> throw KtxResponseException(msgTxt, msgCode)
            }
        }
    }

    fun getOutputArray(json: JSONObject, key: String): JSONArray {
        return json.optJSONObject("outDataSets")
            ?.optJSONArray(key)
            ?: JSONArray()
    }

    fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = obj.opt(key)?.toString()
        }
        return map
    }

    fun jsonArrayToListOfMaps(array: JSONArray): List<Map<String, Any?>> {
        val list = mutableListOf<Map<String, Any?>>()
        for (i in 0 until array.length()) {
            list.add(jsonObjectToMap(array.getJSONObject(i)))
        }
        return list
    }
}

data class SrtParsedResponse(
    val success: Boolean,
    val message: String,
    val json: JSONObject
)
