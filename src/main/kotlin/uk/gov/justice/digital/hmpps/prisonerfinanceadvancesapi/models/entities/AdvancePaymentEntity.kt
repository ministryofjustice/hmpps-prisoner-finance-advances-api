package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceRepaymentResponse
import java.time.Instant
import java.util.UUID

@Entity
@Table(
  name = "advance_record_payments",
  uniqueConstraints = [
    UniqueConstraint(columnNames = ["transaction_id"]),
  ],
)
class AdvancePaymentEntity(

  @Id
  var id: UUID = UUID.randomUUID(),

  @Column(name = "advance_record_id", nullable = false, unique = false)
  var advanceRecordId: UUID,

  @Column(name = "transaction_id", nullable = false, unique = true)
  var transactionId: UUID,

  // the posting for the prisoner subAccount
  @Column(name = "posting_type", nullable = false, unique = false)
  var prisonerPostingType: PostingType,

  @Column(name = "amount", nullable = false, unique = false)
  var amount: Long,

  @Column(name = "timestamp", nullable = false, unique = false)
  var timestamp: Instant,

  @Column(name = "created_by", nullable = false, unique = false)
  var createdBy: String,
) {

  fun toResponse(legacyTransactionId: Long?, description: String) = AdvanceRepaymentResponse(
    id = this.id,
    advanceId = this.advanceRecordId,
    amount = this.amount,
    createdAt = this.timestamp,
    createdBy = this.createdBy,
    legacyTransactionId = legacyTransactionId,
    transactionId = this.transactionId,
    description = description,
  )
}
