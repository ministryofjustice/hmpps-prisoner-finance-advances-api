package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.config.ROLE_PRISONER_FINANCE__ADVANCES__RW
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration.wiremock.GeneralLedgerApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.enums.AdvanceStatus
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.request.CreateAdvanceRepaymentRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceRepaymentResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.services.InMemoryAccountCache
import java.time.Instant
import java.util.UUID

@ExtendWith(GeneralLedgerApiExtension::class, HmppsAuthApiExtension::class)
class AdvanceRepaymentTest : IntegrationTestBase() {

  @Autowired lateinit var memoryAccountCache: InMemoryAccountCache

  @BeforeEach
  fun clearDB() {
    integrationTestHelpers.clearDB()
    hmppsAuth.stubGrantToken()
    generalLedgerApi.resetAll()
    memoryAccountCache.clear()
  }

  val prisonerNumber = "A1234AA"
  val prisonId = "LEI"
  val prisonerSubAccountId: UUID = UUID.randomUUID()
  val prisonSubAccountId: UUID = UUID.randomUUID()
  val prisonParentAccountId: UUID = UUID.randomUUID()
  val prisonerParentAccountId: UUID = UUID.randomUUID()

  @Test
  fun `should repay an advance and return 201`() {
    val advanceCreated = this.integrationTestHelpers.createAdvance(
      prisonNumber = prisonerNumber,
      legacyPaymentProfileId = 123,
      legacyInformationNumber = "1234",
      amount = 10,
      prisonId = prisonId,
      repaymentAmount = 5,
      status = AdvanceStatus.ACTIVE,
      prisonerSubAccountId = prisonerSubAccountId,
      prisonSubAccountId = prisonSubAccountId,
      prisonParentAccountId = prisonParentAccountId,
      prisonerParentAccountId = prisonerParentAccountId,
    )

    val request = CreateAdvanceRepaymentRequest(
      amount = 1,
      legacyTransactionId = 22222,
      createdAt = Instant.now(),
      createdBy = "TEST",
      description = "Test description",
    )

    val response = webTestClient.post().uri("/advances/${advanceCreated.id}/repay")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
      .headers(setIdempotencyKey(UUID.randomUUID()))
      .bodyValue(request)
      .exchange()
      .expectStatus()
      .isCreated
      .expectBody<AdvanceRepaymentResponse>()
      .returnResult()
      .responseBody!!

    assertThat(response.id).isNotNull()
    assertThat(response.advanceId).isEqualTo(advanceCreated.id)
    assertThat(response.amount).isEqualTo(request.amount)
    assertThat(response.createdAt).isEqualTo(request.createdAt)
    assertThat(response.createdBy).isEqualTo(request.createdBy)
    assertThat(response.legacyTransactionId).isEqualTo(request.legacyTransactionId)
  }

  @Test
  fun `Should return 404 if the advanceId does not exist`() {
  }
}
