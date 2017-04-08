package net.corda.node.services.transactions

import io.atomix.copycat.Command
import io.atomix.copycat.Query
import io.atomix.copycat.server.Commit
import io.atomix.copycat.server.Snapshottable
import io.atomix.copycat.server.StateMachine
import io.atomix.copycat.server.storage.snapshot.SnapshotReader
import io.atomix.copycat.server.storage.snapshot.SnapshotWriter
import net.corda.core.utilities.loggerFor
import net.corda.node.utilities.JDBCHashMap
import net.corda.node.utilities.databaseTransaction
import org.jetbrains.exposed.sql.Database
import java.util.*

/**
 * A distributed map state machine that doesn't allow overriding values. The state machine is replicated
 * across a Copycat Raft cluster.
 *
 * The map contents are backed by a JDBC table. State re-synchronisation is achieved by periodically persisting snapshots
 * to disk, and sharing them across the cluster. A new node joining the cluster will have to obtain and install a snapshot
 * containing the entire JDBC table contents.
 */
class DistributedImmutableMap<K : Any, V : Any>(val db: Database, tableName: String) : StateMachine(), Snapshottable {
    companion object {
        private val log = loggerFor<DistributedImmutableMap<*, *>>()
    }

    object Commands {
        class PutAll<K, V>(val entries: Map<K, V>) : Command<Map<K, V>> {
            override fun compaction(): Command.CompactionMode {
                // The SNAPSHOT compaction mode indicates that a command can be removed from the Raft log once
                // a snapshot of the state machine has been written to disk
                return Command.CompactionMode.SNAPSHOT
            }
        }

        class Size : Query<Int>
        class Get<out K, V>(val key: K) : Query<V?>
    }

    private val map = databaseTransaction(db) { JDBCHashMap<K, V>(tableName) }

    /** Gets a value for the given [Commands.Get.key] */
    fun get(commit: Commit<Commands.Get<K, V>>): V? {
        try {
            val key = commit.operation().key
            return databaseTransaction(db) { map[key] }
        } finally {
            commit.close()
        }
    }

    /**
     * Stores the given [Commands.PutAll.entries] if no entry key already exists.
     *
     * @return map containing conflicting entries
     */
    fun put(commit: Commit<Commands.PutAll<K, V>>): Map<K, V> {
        try {
            val conflicts = LinkedHashMap<K, V>()
            databaseTransaction(db) {
                val entries = commit.operation().entries
                log.debug("State machine commit: storing entries with keys (${entries.keys.joinToString()})")
                for (key in entries.keys) map[key]?.let { conflicts[key] = it }
                if (conflicts.isEmpty()) map.putAll(entries)
            }
            return conflicts
        } finally {
            commit.close()
        }
    }

    fun size(commit: Commit<Commands.Size>): Int {
        try {
            return databaseTransaction(db) { map.size }
        } finally {
            commit.close()
        }
    }

    /**
     * Writes out all [map] entries to disk. Note that this operation does not load all entries into memory, as the
     * [SnapshotWriter] is using a disk-backed buffer internally, and iterating map entries results in only a
     * fixed number of recently accessed entries to ever be kept in memory.
     */
    override fun snapshot(writer: SnapshotWriter) {
        databaseTransaction(db) {
            writer.writeInt(map.size)
            map.entries.forEach { writer.writeObject(it.key to it.value) }
        }
    }

    /** Reads entries from disk and adds them to [map]. */
    override fun install(reader: SnapshotReader) {
        val size = reader.readInt()
        databaseTransaction(db) {
            map.clear()
            // TODO: read & put entries in batches
            for (i in 1..size) {
                val (key, value) = reader.readObject<Pair<K, V>>()
                map.put(key, value)
            }
        }
    }
}