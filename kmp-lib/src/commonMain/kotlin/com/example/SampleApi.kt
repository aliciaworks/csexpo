// Sample public API of the KMP library. This is the ONLY Kotlin code the library
// author writes. The csexpo generator reads the compiled klib and produces C# bindings.
package com.example

/**
 * A user of the demo application.
 */
data class User(val name: String, val age: Int)

/**
 * A greeter that produces personalized greetings.
 */
class Greeter(private val prefix: String) {
    fun greet(user: User): String = "$prefix, ${user.name}!"

    val version: Int = 1

    override fun toString(): String = "Greeter($prefix)"
}

/**
 * Basic color palette.
 */
enum class Color(val rgb: Int) {
    RED(0xFF0000),
    GREEN(0x00FF00),
    BLUE(0x0000FF),
}

/** Top-level function: greet a user. */
fun topLevelGreeting(user: User): String = "Hello, ${user.name}!"

/** Top-level function: add two integers. */
fun add(a: Int, b: Int): Int = a + b

/** Top-level function: extract user names. */
fun listNames(users: List<User>): List<String> = users.map { it.name }
