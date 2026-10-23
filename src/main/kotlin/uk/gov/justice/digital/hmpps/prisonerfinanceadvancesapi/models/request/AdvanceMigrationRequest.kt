package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.request

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.entities.AdvanceBalanceMigrationEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.entities.AdvanceRecordEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.enums.AdvanceStatus
import java.time.Instant

data class AdvanceMigrationRequest(
  @field:Schema(description = "The payment profile id from NOMIS", example = "123456789", required = true)
  val legacyPaymentProfileId: Long,

  @field:Schema(description = "The information number for the advance from NOMIS", example = "12345678-1", required = true)
  val legacyInformationNumber: String,

  @field:Schema(description = "The prison number of the offender this advance was for", example = "A9917EC", required = true)
  val prisonNumber: String,

  @field:Schema(description = "The prison ID that issued this advance", example = "LEI", required = true)
  val prisonID: String,

  @field:Schema(description = "The total amount in pence issued in the advance", example = "500", required = true)
  val amountIssued: Long,

  @field:Schema(description = "The outstanding amount in pence at the time of the migration", example = "450", required = true)
  val amountOutstanding: Long,

  @field:Schema(description = "The date time when the advance was created with the time set to midnight", example = "2024-06-18T00:00:00.000000", required = true)
  val createdOn: Instant,

  @field:Schema(description = "The date time the advance was migrated", example = "2024-06-18T12:05:38.000000", required = true)
  val migrationTimestamp: Instant,

  @field:Schema(description = "The date time when the payments are intended to begin", example = "2024-06-18T00:00:00.000000", required = true)
  val repaymentStartDate: Instant,

  @field:Schema(description = "The amount in pence to be repaid weekly", example = "50", required = true)
  val repaymentAmount: Long,

  @field:Schema(description = "The reference from the payment profile ", example = "FNC", required = false)
  val reference: String? = null,

  @field:Schema(description = "The comment from the payment profile ", example = "A comment about this advance", required = false)
  val comment: String? = null,

  @field:Schema(description = "The username that created this advance", example = "JOHN_USER", required = true)
  val createdBy: String,

  @field:Schema(description = "The current status of this advance", example = "AdvanceStatus.ACTIVE", required = true)
  val status: AdvanceStatus,

  @field:Schema(description = "The legacy transaction ID from NOMIS", example = "12345", required = false)
  val legacyTransactionId: Long? = null,
) {
  fun toAdvanceRecordEntity(updateTime: Instant = Instant.now()) = AdvanceRecordEntity(
    legacyPaymentProfileId = this.legacyPaymentProfileId,
    legacyInformationNumber = this.legacyInformationNumber,
    prisonNumber = this.prisonNumber,
    prisonID = this.prisonID,
    amount = this.amountIssued,
    createdOn = this.createdOn,
    repaymentStartDate = this.repaymentStartDate,
    repaymentAmount = this.repaymentAmount,
    reference = this.reference,
    createdBy = this.createdBy,
    status = this.status,
    updatedAt = updateTime,
  )

  fun toAdvanceBalanceMigrationEntity(advanceRecord: AdvanceRecordEntity) = AdvanceBalanceMigrationEntity(
    advanceRecord = advanceRecord,
    amountOutstanding = this.amountOutstanding,
    timestamp = this.migrationTimestamp,
  )
}
