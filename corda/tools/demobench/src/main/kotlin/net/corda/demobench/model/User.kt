@file:JvmName("User")
package net.corda.demobench.model

import net.corda.nodeapi.User
import java.util.*

fun User.toMap(): Map<String, Any> = mapOf(
    "user" to username,
    "password" to password,
    "permissions" to permissions
)

@Suppress("UNCHECKED_CAST")
fun toUser(map: Map<String, Any>) = User(
    map.getOrElse("user", { "none" }) as String,
    map.getOrElse("password", { "none" }) as String,
    LinkedHashSet<String>(map.getOrElse("permissions", { emptyList<String>() }) as Collection<String>)
)

fun user(name: String) = User(name, "letmein", setOf("ALL"))
