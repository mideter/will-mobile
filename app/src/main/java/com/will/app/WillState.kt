package com.will.app

import will.v1.MessengerOuterClass.Word

enum class Connection { Connecting, Ready, Reconnecting }

enum class Reply { None, Pending, Granted }

data class RoomItem(val name: String, val outer: Boolean, val testator: String = "", val novice: String = "") {
    val said: String get() = roomSaid(name, testator)
    val who: String get() = testator.ifEmpty { novice.ifEmpty { said } }
    val role: String? get() = if (testator.isNotEmpty()) "тренер" else if (novice.isNotEmpty()) "ведение" else null
}

fun roomSaid(name: String, testator: String): String = if (testator.isEmpty()) name else "Тренер — $testator"

data class Notice(val text: String, val room: String? = null, val suppliant: String? = null)

data class Waiting(val room: String, val word: WordItem, val counterpart: String, val underway: UnderwayItem? = null)

data class ApproachItem(val weightGrams: Int, val repetitions: Int, val restSeconds: Int = 0)

data class ExerciseItem(val name: String, val approaches: List<ApproachItem>, val instruction: String = "")

data class EffortItem(
    val exercise: Int,
    val approach: Int,
    val weightGrams: Int,
    val repetitions: Int,
    val begunAtNs: Long,
    val finishedAtNs: Long,
    val strain: Int = 0,
)

data class RemarkItem(val exercise: Int, val body: String, val saidAtNs: Long)

data class UnderwayItem(val behestId: Long, val exercise: Int, val approach: Int, val begunAtNs: Long, val endedAtNs: Long? = null)

data class WordItem(
    val id: Long,
    val author: String,
    val body: String,
    val mine: Boolean,
    val kind: Word.Kind,
    val behestId: Long,
    val exercises: List<ExerciseItem> = emptyList(),
    val efforts: List<EffortItem> = emptyList(),
    val remarks: List<RemarkItem> = emptyList(),
)

enum class Kind { Acquaintance, Neighbour, Friend }

enum class Standing { Asked, Asks, Trainer, Novice }

data class Person(val name: String, val kind: Kind, val standings: Set<Standing> = emptySet())

enum class Aspect { Words, Threshold, Dwellers }

sealed interface View {
    val host: String

    data class Abode(
        override val host: String,
        val rooms: List<RoomItem> = emptyList(),
        val owed: List<Waiting> = emptyList(),
        val awaited: List<Waiting> = emptyList(),
        val unreviewed: List<Waiting> = emptyList(),
        val waiting: Int = 0,
    ) : View

    data class Room(
        override val host: String,
        val room: String,
        val aspect: Aspect = Aspect.Words,
        val writable: Boolean = false,
        val words: List<WordItem> = emptyList(),
        val gatesOpen: Boolean = false,
        val keeping: Boolean = false,
        val knocking: Boolean = false,
        val waiting: List<String> = emptyList(),
        val people: List<Person> = emptyList(),
        val unborn: List<Long> = emptyList(),
        val underways: Map<Long, UnderwayItem> = emptyMap(),
        val rooms: List<RoomItem> = emptyList(),
        val testator: String = "",
    ) : View {
        val said: String get() = roomSaid(room, testator)
    }
}

data class Workout(val host: String, val room: String, val behestId: Long)

data class WillState(
    val connection: Connection = Connection.Connecting,
    val ownName: String = "",
    val view: View? = null,
    val loading: Boolean = true,
    val dwellers: List<Person> = emptyList(),
    val dwellings: List<Person> = emptyList(),
    val supplications: List<String> = emptyList(),
    val unbornMark: Long? = null,
    val namedBy: String? = null,
    val willing: Reply = Reply.None,
    val fulfilling: Reply = Reply.None,
    val beginning: Reply = Reply.None,
    val telling: Reply = Reply.None,
    val remarking: Reply = Reply.None,
    val choice: Choice = Choice(),
    val workout: Workout? = null,
)
