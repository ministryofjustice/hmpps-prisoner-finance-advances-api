package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

class AdvanceRepaymentResponse(
  @field:Schema(description = "ID of the repayment", required = true)
  val id: UUID,

  @field:Schema(description = "ID of the advance linked to this repayment", required = true)
  val advanceId: UUID,

  @field:Schema(description = "The amount repaid ", example = "50", required = true)
  val amount: Long,

  @field:Schema(description = "The date time when the advance repayment was sent", example = "2024-06-18T00:00:00.000000", required = true)
  val createdAt: Instant,

  @field:Schema(description = "The username that created this repayment", example = "JOHN_USER", required = true)
  val createdBy: String,

  @field:Schema(description = "The legacy transaction ID from NOMIS", example = "12345", required = false)
  val legacyTransactionId: Long?,

  @field:Schema(description = "The transaction ID from GL", required = false)
  val transactionId: UUID,

  @field:Schema(description = "The description of the repayment transaction", example = "A description about this repayment", required = true)
  val description: String,
)
