package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.time.LocalDate

class AdvanceBalanceResponse(
  @field:Schema(description = "Initial amount of the advance")
  val advanceAmount: Long,

  @field:Schema(description = "Remaining balance to pay")
  val balanceOutstanding: Long,

  @field:Schema(description = "Amount paid each week")
  val weeklyPaymentAmount: Long,

  @field:Schema(description = "remaining payments (balanceOutstanding / weeklyPayments) rounded up")
  val remainingPayments: Long,

  @field:Schema(description = "Time of the balance calculation")
  val balanceDateTime: Instant = Instant.now(),

  @field:Schema(description = "Date of the next due repayment")
  val nextPaymentDate: LocalDate?,
)
