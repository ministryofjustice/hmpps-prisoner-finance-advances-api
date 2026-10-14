package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.config.ROLE_PRISONER_FINANCE__ADVANCES__RO
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.config.ROLE_PRISONER_FINANCE__ADVANCES__RW
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration.wiremock.GeneralLedgerApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.entities.AdvanceRecordEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.enums.AdvanceStatus
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.generalledger.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.request.CreateAdvanceRepaymentRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceRepaymentResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.PagedResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.repositories.AdvanceRecordRepository
import java.time.Instant
import java.util.UUID

@ExtendWith(GeneralLedgerApiExtension::class, HmppsAuthApiExtension::class)
class AdvanceRepaymentTest : IntegrationTestBase() {

  @Autowired lateinit var advanceRecordRepository: AdvanceRecordRepository

  @BeforeEach
  fun clearDB() {
    integrationTestHelpers.clearDB()
    hmppsAuth.stubGrantToken()
    generalLedgerApi.resetAll()
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
      entrySequence = 2,
    )

    val glUUID = UUID.randomUUID()
    generalLedgerApi.stubPostTransaction(
      creditorSubAccountUuid = prisonSubAccountId.toString(),
      debtorSubAccountUuid = prisonerSubAccountId.toString(),
      returnUUID = glUUID,
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
    assertThat(response.entrySequence).isEqualTo(request.entrySequence)
    assertThat(response.description).isEqualTo(request.description)
    assertThat(response.transactionId).isEqualTo(glUUID)
  }

  @Test
  fun `should change the status to repaid if the repayment zeros the balance`() {
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
      entrySequence = 2,
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

  @Test
  fun `should return 400 if the advanceId is not a UUID`() {
    val request = CreateAdvanceRepaymentRequest(
      amount = 111,
      legacyTransactionId = 22222,
      createdAt = Instant.now(),
      createdBy = "TEST",
      description = "Test description",
      entrySequence = 2,
    )

    val response = webTestClient.post().uri("/advances/INVALID/repay")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
      .headers(setIdempotencyKey(UUID.randomUUID()))
      .bodyValue(request)
      .exchange()
      .expectStatus()
      .isBadRequest
      .expectBody<ErrorResponse>()
      .returnResult()
      .responseBody!!

    assertThat(response.userMessage).isEqualTo("Parameter 'advanceId' must be of type UUID")
    assertThat(response.developerMessage).contains("Method parameter 'advanceId': Failed to convert value of type 'java.lang.String' to required type 'java.util.UUID'")
  }

  @Test
  fun `should return 400 if the idempotency is missing`() {
    val request = CreateAdvanceRepaymentRequest(
      amount = 11,
      legacyTransactionId = 22222,
      createdAt = Instant.now(),
      createdBy = "TEST",
      description = "Test description",
      entrySequence = 2,
    )

    val response = webTestClient.post().uri("/advances/${UUID.randomUUID()}/repay")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
      .bodyValue(request)
      .exchange()
      .expectStatus()
      .isBadRequest
      .expectBody<ErrorResponse>()
      .returnResult()
      .responseBody!!

    assertThat(response.userMessage).isEqualTo("Validation failure")

    assertThat(response.developerMessage).isEqualTo("Required request header 'Idempotency-Key' for method parameter type UUID is not present")
  }

  @Test
  fun `should return 400 if you are overpaying an advance`() {
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
      amount = 999999999,
      legacyTransactionId = 22222,
      createdAt = Instant.now(),
      createdBy = "TEST",
      description = "Test description",
      entrySequence = 2,
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
      .isBadRequest
      .expectBody<ErrorResponse>()
      .returnResult()
      .responseBody!!

    assertThat(response.userMessage).isEqualTo("Repayments cannot exceed the balance of the advance record")
  }

  @Test
  fun `should return 400 if you are repaying an advance which is repaid`() {
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
      entrySequence = 2,
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

    val requestOverPayment = CreateAdvanceRepaymentRequest(
      amount = 9999,
      legacyTransactionId = 22222,
      createdAt = Instant.now(),
      createdBy = "TEST",
      description = "Test description",
      entrySequence = 2,
    )

    generalLedgerApi.stubPostTransaction(
      creditorSubAccountUuid = prisonSubAccountId.toString(),
      debtorSubAccountUuid = prisonerSubAccountId.toString(),
      returnUUID = UUID.randomUUID(),
      amount = requestOverPayment.amount,
      legacyTransactionId = request.legacyTransactionId.toString(),
    )

    val response = webTestClient.post().uri("/advances/${advanceCreated.id}/repay")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
      .headers(setIdempotencyKey(UUID.randomUUID()))
      .bodyValue(request)
      .exchange()
      .expectStatus()
      .isBadRequest
      .expectBody<ErrorResponse>()
      .returnResult()
      .responseBody!!

    assertThat(response.userMessage).isEqualTo("Cannot repay an advance with the status of REPAID")
  }

  @Test
  fun `should return 400 if you are repaying an advance which is written-off`() {
    // TODO, change this setup once we have a write-off endpoint
    val advanceRecordEntity = AdvanceRecordEntity(
      legacyPaymentProfileId = 234,
      legacyInformationNumber = "123",
      prisonNumber = "A1234BC",
      prisonID = "LEI",
      amount = 123,
      createdOn = Instant.now(),
      repaymentStartDate = Instant.now(),
      repaymentAmount = 1,
      reference = "",
      comment = "",
      createdBy = "TEST",
      updatedAt = Instant.now(),
      status = AdvanceStatus.WRITTEN_OFF,
    )

    advanceRecordRepository.saveAndFlush(advanceRecordEntity)

    val requestPayment = CreateAdvanceRepaymentRequest(
      amount = 12,
      legacyTransactionId = 22222,
      createdAt = Instant.now(),
      createdBy = "TEST",
      description = "Test description",
      entrySequence = 2,
    )

    val response = webTestClient.post().uri("/advances/${advanceRecordEntity.id}/repay")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
      .headers(setIdempotencyKey(UUID.randomUUID()))
      .bodyValue(requestPayment)
      .exchange()
      .expectStatus()
      .isBadRequest
      .expectBody<ErrorResponse>()
      .returnResult()
      .responseBody!!

    assertThat(response.userMessage).isEqualTo("Cannot repay an advance with the status of WRITTEN_OFF")
  }

  @Test
  fun `should return 403 forbidden when using the incorrect role`() {
    val requestPayment = CreateAdvanceRepaymentRequest(
      amount = 12,
      legacyTransactionId = 22222,
      createdAt = Instant.now(),
      createdBy = "TEST",
      description = "Test description",
      entrySequence = 2,
    )

    webTestClient.post().uri("/advances/${UUID.randomUUID()}/repay")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RO)))
      .headers(setIdempotencyKey(UUID.randomUUID()))
      .bodyValue(requestPayment)
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `Should return 404 if the advanceId does not exist`() {
    val request = CreateAdvanceRepaymentRequest(
      amount = 1,
      legacyTransactionId = 22222,
      createdAt = Instant.now(),
      createdBy = "TEST",
      description = "Test description",
      entrySequence = 2,
    )

    webTestClient.post().uri("/advances/${UUID.randomUUID()}/repay")
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

  @Test
  fun `should return 502 when GL service is returning an error`() {
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
      entrySequence = 2,
    )

    generalLedgerApi.resetAll()

    integrationTestHelpers.stubGetSubAccounts(
      prisonId = prisonId,
      prisonNumber = prisonerNumber,
      prisonerSubAccountId = prisonerSubAccountId,
      prisonSubAccountId = prisonSubAccountId,
      prisonerParentAccountId = prisonParentAccountId,
      prisonParentAccountId = prisonerParentAccountId,
    )

    generalLedgerApi.stubPostTransactionReturnsInternalServerError()

    val response = webTestClient.post().uri("/advances/${advanceCreated.id}/repay")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
      .headers(setIdempotencyKey(UUID.randomUUID()))
      .bodyValue(request)
      .exchange()
      .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)
      .expectBody<ErrorResponse>()
      .returnResult()
      .responseBody!!

    assertThat(response.userMessage).isEqualTo("Bad Gateway - General Ledger Unreachable or throwing an error")
  }
}
