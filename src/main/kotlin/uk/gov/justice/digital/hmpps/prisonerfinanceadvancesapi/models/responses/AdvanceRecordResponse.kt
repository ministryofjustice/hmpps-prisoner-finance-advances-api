package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.entities.AdvanceRecordEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.enums.AdvanceStatus
import java.time.Instant
import java.util.UUID
import kotlin.String

data class AdvanceRecordResponse(
  @field:Schema(description = "ID of the repayment")
  val id: UUID,

  @field:Schema(description = "The payment profile id from NOMIS", example = "123456789")
  val legacyPaymentProfileId: Long,

  @field:Schema(description = "The information number for the advance from NOMIS", example = "12345678-1")
  val legacyInformationNumber: String,

  @field:Schema(description = "The prison number of the offender this advance was for", example = "A9917EC")
  val prisonNumber: String,

  @field:Schema(description = "The prison ID that issued this advance", example = "LEI")
  val prisonID: String,

  @field:Schema(description = "The amount in pence issued in the advance", example = "500")
  val amount: Long,

  @field:Schema(description = "The date time when the advance was created with the time set to midnight", example = "2024-06-18T00:00:00.000000")
  val createdOn: Instant,

  @field:Schema(description = "The date time when the payments are intended to begin", example = "2024-06-18T00:00:00.000000")
  val repaymentStartDate: Instant,

  @field:Schema(description = "The amount in pence to be repaid weekly", example = "50")
  val repaymentAmount: Long,

  @field:Schema(description = "The reference from the payment profile ", example = "FNC")
  val reference: String? = null,

  @field:Schema(description = "The comment from the payment profile ", example = "A comment about this advance")
  val comment: String? = null,

  @field:Schema(description = "The username that created this advance", example = "JOHN_USER")
  val createdBy: String,

  @field:Schema(description = "The current status of this advance", example = "AdvanceStatus.ACTIVE")
  val status: AdvanceStatus,

  @field:Schema(description = "Last time this advance record was changed")
  val updatedAt: Instant? = null,

  @field:Schema(description = "The transaction UUID from General Ledger if one was created")
  val generalLedgerUUID: UUID?,
) {
  companion object {
    fun fromEntity(advanceRecordEntity: AdvanceRecordEntity, generalLedgerUUID: UUID? = null) = AdvanceRecordResponse(
      id = advanceRecordEntity.id,
      legacyPaymentProfileId = advanceRecordEntity.legacyPaymentProfileId,
      legacyInformationNumber = advanceRecordEntity.legacyInformationNumber,
      prisonNumber = advanceRecordEntity.prisonNumber,
      prisonID = advanceRecordEntity.prisonID,
      amount = advanceRecordEntity.amount,
      createdOn = advanceRecordEntity.createdOn,
      repaymentStartDate = advanceRecordEntity.repaymentStartDate,
      repaymentAmount = advanceRecordEntity.repaymentAmount,
      reference = advanceRecordEntity.reference,
      comment = advanceRecordEntity.comment,
      createdBy = advanceRecordEntity.createdBy,
      status = advanceRecordEntity.status,
      updatedAt = advanceRecordEntity.updatedAt,
      generalLedgerUUID = generalLedgerUUID,
    )
  }
}
