package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.request

import java.time.Instant

data class AdvanceWriteOffRequest(
  val writeOffDateTime: Instant,
  val writtenOffBy: String,
  val writtenOffAmount: Long,
)
