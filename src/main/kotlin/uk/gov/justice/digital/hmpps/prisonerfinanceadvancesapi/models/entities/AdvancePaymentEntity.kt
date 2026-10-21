package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvancePrisonerRepaymentResponse
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

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "advance_record_id", nullable = false)
  var advanceRecord: AdvanceRecordEntity,

  @Column(name = "transaction_id", nullable = false, unique = true)
  var transactionId: UUID,

  // the posting for the prisoner subAccount
  @Enumerated(EnumType.STRING)
  @Column(name = "posting_type", nullable = false, unique = false)
  var prisonerPostingType: PostingType,

  @Column(name = "amount", nullable = false, unique = false)
  var amount: Long,

  @Column(name = "timestamp", nullable = false, unique = false)
  var timestamp: Instant,

  @Column(name = "created_by", nullable = false, unique = false)
  var createdBy: String,
) {

  fun toResponse(legacyTransactionId: Long?, description: String, entrySequence: Int) = AdvanceRepaymentResponse(
    id = this.id,
    advanceId = this.advanceRecord.id,
    amount = this.amount,
    createdAt = this.timestamp,
    createdBy = this.createdBy,
    legacyTransactionId = legacyTransactionId,
    transactionId = this.transactionId,
    description = description,
    entrySequence = entrySequence,
  )

  fun toPrisonerRepaymentResponse() = AdvancePrisonerRepaymentResponse(
    id = this.id,
    advanceId = this.advanceRecord.id,
    amount = this.amount,
    createdAt = this.timestamp,
    createdBy = this.createdBy,
    transactionId = this.transactionId,
    location = this.advanceRecord.prisonID,
  )
}
