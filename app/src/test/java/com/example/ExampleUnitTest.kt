package com.example

import com.example.api.SolanaSecurity
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun testUserPrivateKey() {
    val key = "2rUJTEKUDjug9rxSFy4xE2u5Jsrg18EU3XqWzXhWxBaKoCHmVUVmY2pApF2rqTmpggNwJFEXuJZHjY1AHNtdKwcU"
    
    val decoded = SolanaSecurity.Base58.decode(key)
    assertEquals(64, decoded.size)
    
    val pubkeyBytes = decoded.copyOfRange(32, 64)
    val encodedPubkey = SolanaSecurity.Base58.encode(pubkeyBytes)
    assertEquals(44, encodedPubkey.length)
    
    val finalAddress = SolanaSecurity.derivePublicKey(key)
    assertEquals(encodedPubkey, finalAddress)
    assertEquals(44, finalAddress.length)
    println("SUCCESSFULLY DERIVED SOLANA WALLET ADDRESS: $finalAddress")
  }

  @Test
  fun testDefaultPrivateKey() {
    val key = "2rUJTEkUDjuG9rxSfY4xE2u5Jsrg18EU3XqWzXhWxBaKoCHmVUVmY2pApF2rqTmpggNwJFEXuJZHjY1AHNtdKwcU"
    try {
      val decoded = SolanaSecurity.Base58.decode(key)
      println("DEFAULT KEY DECODED SIZE: ${decoded.size}")
      val finalAddress = SolanaSecurity.derivePublicKey(key)
      println("DEFAULT KEY DERIVED ADDRESS: $finalAddress")
    } catch (e: Exception) {
      fail("Default key failed: ${e.message}")
    }
  }
}
