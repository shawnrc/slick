package me.shawnrc.slick

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.request.path
import io.ktor.request.receiveText
import io.ktor.response.respond
import io.ktor.response.respondBytes
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.event.Level

fun main(args: Array<String>) {
  val port = System.getenv("PORT")?.toInt() ?: 8080
  val redisUrl = System.getenv("REDIS_URL") ?: "localhost"
  val redis = RedisManager(redisUrl)
  val parser = Klaxon()

  embeddedServer(Netty, port) {
    install(CallLogging) {
      level = Level.INFO
      filter { it.request.path() != "/healthcheck" }
    }

    routing {
      get("healthcheck") { call.respond("OK") }

      route("user") {
        post {
          val blob = call.receiveText()
          if (blob.isBlank()) {
            call.respond(HttpStatusCode.BadRequest)
          } else {
            val profile = parser.parseJsonObject(blob.reader())
            val maybeId = profile.string("id")
            if (maybeId == null) {
              call.respond(HttpStatusCode.BadRequest, "missing id field")
            } else {
              redis.setUser(maybeId, blob)
              call.respond(HttpStatusCode.Accepted)
            }
          }

        }

        get("{id}") {
          val id = call.parameters["id"] ?: ""
          val maybeUser = redis.getUser(id)
          if (maybeUser == null) {
            call.respond(status = HttpStatusCode.NotFound, message = "")
          } else {
            call.respondBytes(ContentType.Application.Json) {
              maybeUser.toByteArray()
            }
          }
        }
      }

      post("event") {
        val blob = parser.parseJsonObject(call.receiveText().reader())
        val maybeType = blob.string("type")

        if (maybeType == "url_verification") {
          call.respond(blob.string("challenge") ?: "")
        } else {
          val event = blob.getObject("event")
          val type = event.getString("type")
          if (type == "user_change") {
            val userObject = event.getObject("user")
            val userId = userObject.getString("id")
            val profile = userObject.getObject("profile")
            redis.setUser(userId, profile.toJsonString())
          }
        }
      }
    }
  }.start(wait = true)
}

private fun JsonObject.getObject(field: String) =
    obj(field) ?: throw Exception("missing field $field")

private fun JsonObject.getString(field: String) =
    string(field) ?: throw Exception("missing field $field")
