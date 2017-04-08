package net.corda.schemas

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.time.Instant
import javax.persistence.*

/**
 * An object used to fully qualify the [CashSchema] family name (i.e. independent of version).
 */
object ShareSchema

/**
 * First version of a cash contract ORM schema that maps all fields of the [Share] contract state as it stood
 * at the time of writing. - MTODO: implement the Share contract
 */
object ShareSchemaV1 : MappedSchema(schemaFamily = ShareSchema.javaClass, version = 1, mappedTypes = listOf(PersistentShareState::class.java)) {
    @Entity
    @Table(name = "share_states")
    class PersistentShareState(
            @Column(name = "owner_key")
            var owner: String,

            @Column(name = "qty")
            var qty: Long,

            @Column(name = "ccy_code", length = 3)
            var currency: String,

            @Column(name = "face_value")
            var faceValue: Long,

            @Column(name = "ticker")
            var ticker: String,

            @Column(name = "issuer_key")
            var issuerParty: String,

            @Column(name = "issuer_ref")
            var issuerRef: ByteArray,

            @Column(name = "maturity_instant")
            var maturity: Instant,

            @Column(name = "face_value_issuer_key")
            var faceValueIssuerParty: String,

            @Column(name = "face_value_issuer_ref")
            var faceValueIssuerRef: ByteArray

            ) : PersistentState()
}
