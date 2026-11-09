package com.example.api

import android.util.Log
import java.security.MessageDigest

object SolanaSecurity {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val ALPHABET_CHAR = ALPHABET.toCharArray()
    private val INDEXES = IntArray(128) { -1 }.apply {
        for (i in ALPHABET.indices) {
            this[ALPHABET[i].code] = i
        }
    }

    object Base58 {
        fun encode(input: ByteArray): String {
            if (input.isEmpty()) return ""
            var zeros = 0
            while (zeros < input.size && input[zeros] == 0.toByte()) {
                zeros++
            }
            
            val temp = ByteArray(input.size - zeros)
            System.arraycopy(input, zeros, temp, 0, temp.size)
            
            if (temp.isEmpty()) {
                return "1".repeat(zeros)
            }
            
            var num = java.math.BigInteger(1, temp)
            val sb = java.lang.StringBuilder()
            val fiftyEight = java.math.BigInteger.valueOf(58)
            while (num > java.math.BigInteger.ZERO) {
                val mod = num.mod(fiftyEight)
                sb.append(ALPHABET_CHAR[mod.toInt()])
                num = num.divide(fiftyEight)
            }
            
            val result = sb.reverse().toString()
            return "1".repeat(zeros) + result
        }

        fun decode(input: String): ByteArray {
            if (input.isEmpty()) return ByteArray(0)
            var num = java.math.BigInteger.ZERO
            for (i in 0 until input.length) {
                val c = input[i]
                val digit = if (c.code < 128) INDEXES[c.code] else -1
                if (digit < 0) {
                    throw IllegalArgumentException("Invalid Base58 character: $c")
                }
                num = num.multiply(java.math.BigInteger.valueOf(58)).add(java.math.BigInteger.valueOf(digit.toLong()))
            }
            
            val bytes = num.toByteArray()
            val start = if (bytes.isNotEmpty() && bytes[0] == 0.toByte()) 1 else 0
            val rawBytes = if (start == 1) bytes.copyOfRange(1, bytes.size) else bytes
            
            var leadingZeros = 0
            while (leadingZeros < input.length && input[leadingZeros] == '1') {
                leadingZeros++
            }
            
            val result = ByteArray(leadingZeros + rawBytes.size)
            System.arraycopy(rawBytes, 0, result, leadingZeros, rawBytes.size)
            return result
        }
    }

    /**
     * Standard Phantom private key is 64 bytes total:
     * First 32 bytes are seed, last 32 bytes are public key.
     */
    fun derivePublicKey(privateKeyStr: String): String {
        var baseKey = privateKeyStr.trim()
            .removePrefix("\"").removeSuffix("\"")
            .removePrefix("'").removeSuffix("'")
            .trim()
        if (baseKey.isEmpty()) return ""

        val cleaned = if (baseKey.startsWith("[") && baseKey.endsWith("]")) {
            baseKey.replace("\\s".toRegex(), "")
                .replace("\n", "")
                .replace("\r", "")
                .replace("\t", "")
                .replace(" ", "")
        } else {
            baseKey.replace("\\s".toRegex(), "")
                .replace("\n", "")
                .replace("\r", "")
                .replace("\t", "")
                .replace(" ", "")
                .replace("\\p{Cf}".toRegex(), "")
                .replace("\u200B", "")
                .replace("\uFEFF", "")
        }

        if (cleaned.isEmpty()) return ""

        // If the user pasted a public key / address directly, return it directly. 
        // A valid Solana base58 address is between 32 and 44 characters.
        if (cleaned.length in 32..44 && !cleaned.contains(",")) {
            try {
                Base58.decode(cleaned)
                return cleaned
            } catch (e: Exception) {
                // fall through
            }
        }

        return try {
            val bytes = when {
                cleaned.startsWith("[") && cleaned.endsWith("]") -> {
                    val arrayContent = cleaned.substring(1, cleaned.length - 1)
                    if (arrayContent.isEmpty()) ByteArray(0)
                    else {
                        val parts = arrayContent.split(",")
                        val byteArray = ByteArray(parts.size)
                        for (i in parts.indices) {
                            val value = parts[i].trim().toIntOrNull() ?: 0
                            byteArray[i] = value.toByte()
                        }
                        byteArray
                    }
                }
                cleaned.length == 64 || cleaned.length == 128 -> {
                    if (cleaned.all { "0123456789abcdefABCDEF".contains(it) }) {
                        val len = cleaned.length
                        val data = ByteArray(len / 2)
                        for (i in 0 until len step 2) {
                            data[i / 2] = ((Character.digit(cleaned[i], 16) shl 4) + Character.digit(cleaned[i + 1], 16)).toByte()
                        }
                        data
                    } else {
                        Base58.decode(cleaned)
                    }
                }
                else -> {
                    Base58.decode(cleaned)
                }
            }

            if (bytes.size == 64) {
                val pubkeyBytes = bytes.copyOfRange(32, 64)
                Base58.encode(pubkeyBytes)
            } else if (bytes.size == 32) {
                // Return base58 encoding of 32 bytes directly
                Base58.encode(bytes)
            } else {
                ""
            }
        } catch (e: Exception) {
            try {
                Log.e("SolanaSecurity", "derivation error: ${e.message}")
            } catch (t: Throwable) {
                println("SolanaSecurity derivation error: ${e.message}")
                e.printStackTrace()
            }
            ""
        }
    }
}
