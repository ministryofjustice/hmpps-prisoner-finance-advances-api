package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

class AdvancePrisonerRepaymentResponse(
  @field:Schema(description = "ID of the repayment")
  val id: UUID,

  @field:Schema(description = "ID of the advance linked to this repayment")
  val advanceId: UUID,

  @field:Schema(description = "The amount repaid in pence", example = "50")
  val amount: Long,

  @field:Schema(description = "The date time when the advance repayment was sent", example = "2024-06-18T00:00:00.000000")
  val createdAt: Instant,

  @field:Schema(description = "The username that created this repayment", example = "JOHN_USER")
  val createdBy: String,

  @field:Schema(description = "The transaction ID from GL")
  val transactionId: UUID,

  @field:Schema(description = "The location of the advance", example = "LEI")
  val location: String,
)
