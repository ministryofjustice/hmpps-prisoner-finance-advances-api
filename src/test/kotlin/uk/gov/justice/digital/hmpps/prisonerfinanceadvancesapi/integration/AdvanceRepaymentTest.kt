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
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.generalledger.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.request.CreateAdvanceRepaymentRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceRepaymentResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.PagedResponse
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

    generalLedgerApi.stubPostTransaction(
      creditorSubAccountUuid = prisonSubAccountId.toString(),
      debtorSubAccountUuid = prisonerSubAccountId.toString(),
      returnUUID = UUID.randomUUID(),
      amount = request.amount,
      legacyTransactionId = request.legacyTransactionId.toString(),
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
  fun `should change the status to repaid`() {
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
      amount = 10,
      legacyTransactionId = 22222,
      createdAt = Instant.now(),
      createdBy = "TEST",
      description = "Test description",
    )

    generalLedgerApi.stubPostTransaction(
      creditorSubAccountUuid = prisonSubAccountId.toString(),
      debtorSubAccountUuid = prisonerSubAccountId.toString(),
      returnUUID = UUID.randomUUID(),
      amount = request.amount,
      legacyTransactionId = request.legacyTransactionId.toString(),
    )

    webTestClient.post().uri("/advances/${advanceCreated.id}/repay")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
      .headers(setIdempotencyKey(UUID.randomUUID()))
      .bodyValue(request)
      .exchange()
      .expectStatus()
      .isCreated
      .expectBody<AdvanceRepaymentResponse>()
      .returnResult()
      .responseBody!!

    val prisonerAdvances = webTestClient.get().uri("/advances/${advanceCreated.prisonNumber}")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
      .exchange()
      .expectStatus()
      .isOk
      .expectBody<PagedResponse<AdvanceRecordResponse>>()
      .returnResult()
      .responseBody!!

    assertThat(prisonerAdvances.content).hasSize(1)

    val responseAdvance = prisonerAdvances.content[0]
    assertThat(responseAdvance.id).isEqualTo(advanceCreated.id)
    assertThat(responseAdvance.status).isEqualTo(AdvanceStatus.REPAID)
  }

  /*
  @Test
  fun `should return 400 if you are overpaying an advance`() {}
  @Test
  fun `should return 400 if you are repaying an advance which is repaid`() {}
  @Test
  fun `should return 400 if you are repaying an advance which is written-off`() {}
*/

  @Test
  fun `should respond with 201 when the same transaction is posted twice`() {
    val idempotencyKey = UUID.randomUUID()
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

    generalLedgerApi.stubPostTransaction(
      creditorSubAccountUuid = prisonSubAccountId.toString(),
      debtorSubAccountUuid = prisonerSubAccountId.toString(),
      returnUUID = UUID.randomUUID(),
      amount = request.amount,
      legacyTransactionId = request.legacyTransactionId.toString(),
    )

    val responseOne = webTestClient.post().uri("/advances/${advanceCreated.id}/repay")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
      .headers(setIdempotencyKey(idempotencyKey))
      .bodyValue(request)
      .exchange()
      .expectStatus()
      .isCreated
      .expectBody<AdvanceRepaymentResponse>()
      .returnResult()
      .responseBody!!

    assertThat(responseOne.id).isNotNull()
    assertThat(responseOne.advanceId).isEqualTo(advanceCreated.id)
    assertThat(responseOne.amount).isEqualTo(request.amount)
    assertThat(responseOne.createdAt).isEqualTo(request.createdAt)
    assertThat(responseOne.createdBy).isEqualTo(request.createdBy)
    assertThat(responseOne.legacyTransactionId).isEqualTo(request.legacyTransactionId)

    val responseTwo = webTestClient.post().uri("/advances/${advanceCreated.id}/repay")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
      .headers(setIdempotencyKey(idempotencyKey))
      .bodyValue(request)
      .exchange()
      .expectStatus()
      .isCreated
      .expectBody<AdvanceRepaymentResponse>()
      .returnResult()
      .responseBody!!

    assertThat(responseTwo.id).isEqualTo(responseOne.id)
    assertThat(responseTwo.advanceId).isEqualTo(advanceCreated.id)
    assertThat(responseTwo.amount).isEqualTo(request.amount)
    assertThat(responseTwo.createdAt).isEqualTo(request.createdAt)
    assertThat(responseTwo.createdBy).isEqualTo(request.createdBy)
    assertThat(responseTwo.legacyTransactionId).isEqualTo(request.legacyTransactionId)
  }

  @Test
  fun `Should return 404 if the advanceId does not exist`() {
    val request = CreateAdvanceRepaymentRequest(
      amount = 1,
      legacyTransactionId = 22222,
      createdAt = Instant.now(),
      createdBy = "TEST",
      description = "Test description",
    )

    val response = webTestClient.post().uri("/advances/${UUID.randomUUID()}/repay")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
      .headers(setIdempotencyKey(UUID.randomUUID()))
      .bodyValue(request)
      .exchange()
      .expectStatus()
      .isNotFound
      .expectBody<ErrorResponse>()
      .returnResult()
      .responseBody!!
  }
}
