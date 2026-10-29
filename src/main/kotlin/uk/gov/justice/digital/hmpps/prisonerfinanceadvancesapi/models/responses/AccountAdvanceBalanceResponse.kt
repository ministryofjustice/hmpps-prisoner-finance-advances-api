package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

class AccountAdvanceBalanceResponse(

  @field:Schema(description = "Combined initial amount of all advances on the account in pence")
  val advancesAmount: Long,

  @field:Schema(description = "Combined outstanding amount of all advances on the account in pence")
  val balancesOutstanding: Long,

  @field:Schema(description = "Combined maximum weekly payment amount of all advances on the account in pence")
  val weeklyPaymentAmount: Long,

  @field:Schema(description = "The number of active advances")
  val advanceCount: Int,

  @field:Schema(description = "Time of the balance calculation")
  balanceDateTime: Instant = Instant.now(),
)
