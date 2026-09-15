package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses

import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.entities.AdvanceRecord
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.enums.AdvanceStatus
import java.time.Instant
import java.util.UUID
import kotlin.String

data class AdvanceRecordResponse(
  val id: UUID,
  val legacyPaymentProfileId: String,
  val legacyInformationNumber: String,
  val prisonNumber: String,
  val prisonID: String,
  val amount: Int,
  val createdOn: Instant,
  val repaymentStartDate: Instant,
  val repaymentAmount: Int,
  val reference: String,
  val createdBy: String,
  val status: AdvanceStatus,
) {

  companion object {
    fun fromEntity(advanceRecordEntity: AdvanceRecord) = AdvanceRecordResponse(
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
      createdBy = advanceRecordEntity.createdBy,
      status = advanceRecordEntity.status,
    )
  }
}
