package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.enums.AdvanceStatus
import java.time.Instant
import java.util.UUID

class MergeIntegrationTests : SqsIntegrationTestBase() {
  val mergedPrisonNumber = "A1231XX"
  val survivorPrisonNumber = "A1231YY"

  @BeforeEach
  fun setup() {
    hmppsAuth.stubGrantToken()
  }

  @Test
  fun `should merge advances`() {
    val createdAdvance = integrationTestHelpers.createAdvance(
      prisonNumber = mergedPrisonNumber,
      legacyPaymentProfileId = 12345,
      legacyInformationNumber = "12345-1",
      amount = 100,
      prisonId = "LEI",
      repaymentAmount = 1,
      status = AdvanceStatus.ACTIVE,
      prisonerSubAccountId = UUID.randomUUID(),
      prisonSubAccountId = UUID.randomUUID(),
      prisonParentAccountId = UUID.randomUUID(),
      prisonerParentAccountId = UUID.randomUUID(),
      createdOn = Instant.now(),
      repaymentStartDate = Instant.now().plusSeconds(1),
    )

    val prisonerAdvancesToBeMerged = integrationTestHelpers.getAdvances(mergedPrisonNumber)
    assertThat(prisonerAdvancesToBeMerged.content).hasSize(1)
    assertThat(prisonerAdvancesToBeMerged.content[0].prisonNumber).isEqualTo(createdAdvance.prisonNumber)
    assertThat(prisonerAdvancesToBeMerged.content[0].id).isEqualTo(createdAdvance.id)

    val survivingPrisonerAdvancesPreMerge = integrationTestHelpers.getAdvances(survivorPrisonNumber)
    assertThat(survivingPrisonerAdvancesPreMerge.content).hasSize(0)

    publishMergeEvent(mergedPrisonNumber, survivorPrisonNumber)

    waitUntilEmpty(hmppsQueueService = hmppsQueueService)

    val mergedPrisonerAdvances = integrationTestHelpers.getAdvances(mergedPrisonNumber)
    assertThat(mergedPrisonerAdvances.content).hasSize(0)

    val survivingPrisonerAdvances = integrationTestHelpers.getAdvances(survivorPrisonNumber)
    assertThat(survivingPrisonerAdvances.content).hasSize(1)
    assertThat(survivingPrisonerAdvances.content[0].prisonNumber).isEqualTo(survivorPrisonNumber)
    assertThat(survivingPrisonerAdvances.content[0].id).isEqualTo(createdAdvance.id)
  }
}
