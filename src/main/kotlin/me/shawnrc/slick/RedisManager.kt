package me.shawnrc.slick

import redis.clients.jedis.Jedis

class RedisManager(hostedAt: String, password: String? = null) {
  private val client = Jedis(hostedAt)

  init {
    if (password != null) client.auth(password)
    if (client.ping() != "PONG") throw Exception("could not connect to redis server")
  }

  fun setUser(id: String, userBlob: String) {
    client.use {
      it["users:$id".toByteArray()] = userBlob.toByteArray()
    }
  }

  fun getUser(id: String): ByteArray? {
    return client.use {
      it["users:$id".toByteArray()]
    }
  }
}
