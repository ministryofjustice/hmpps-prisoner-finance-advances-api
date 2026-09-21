package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.request

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.enums.AdvanceStatus
import java.time.Instant

data class CreateAdvanceRecordRequest(

  @field:Schema(description = "The payment profile id from NOMIS", example = "123456789", required = true)
  val legacyPaymentProfileId: Long,

  @field:Schema(description = "The information number for the advance from NOMIS", example = "12345678-1", required = true)
  val legacyInformationNumber: String,

  @field:Schema(description = "The prison number of the offender this advance was for", example = "A9917EC", required = true)
  val prisonNumber: String,

  @field:Schema(description = "The prison ID that issued this advance", example = "LEI", required = true)
  val prisonID: String,

  @field:Schema(description = "The amount in pence issued in the advance", example = "500", required = true)
  val amount: Long,

  @field:Schema(description = "The date time when the advance was created with the time set to midnight", example = "2024-06-18T00:00:00.000000", required = true)
  val createdOn: Instant,

  @field:Schema(description = "The date time when the payments are intended to begin", example = "2024-06-18T00:00:00.000000", required = true)
  val repaymentStartDate: Instant,

  @field:Schema(description = "The amount in pence to be repaid weekly", example = "50", required = true)
  val repaymentAmount: Long,

  @field:Schema(description = "The reference from the payment profile ", example = "FNC", required = true)
  val reference: String,

  @field:Schema(description = "The username that created this advance", example = "JOHN_USER", required = true)
  val createdBy: String,

  @field:Schema(description = "The current status of this advance", example = "AdvanceStatus.ACTIVE", required = true)
  val status: AdvanceStatus,

)
