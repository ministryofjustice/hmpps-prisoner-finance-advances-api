package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.request

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

data class CreateAdvanceRepaymentRequest(
  @field:Schema(description = "The amount repaid ", example = "50", required = true)
  val amount: Long,

  @field:Schema(description = "The legacy transaction ID from NOMIS", example = "12345", required = false)
  val legacyTransactionId: Long?,

  @field:Schema(description = "The date time when the advance repayment was sent", example = "2024-06-18T00:00:00.000000", required = true)
  val createdAt: Instant,

  @field:Schema(description = "The username that created this repayment", example = "JOHN_USER", required = true)
  val createdBy: String,

  @field:Schema(description = "The description of the repayment transaction", example = "A description about this repayment", required = true)
  val description: String,
)
