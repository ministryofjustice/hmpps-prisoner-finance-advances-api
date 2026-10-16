package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration

import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.config.ROLE_PRISONER_FINANCE__ADVANCES__RO
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.config.ROLE_PRISONER_FINANCE__ADVANCES__RW
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration.wiremock.GeneralLedgerApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.enums.AdvanceStatus
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.generalledger.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.request.AdvanceWriteOffRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.request.CreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceRecordResponse
import wiremock.org.eclipse.jetty.http.HttpStatus
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@ExtendWith(GeneralLedgerApiExtension::class, HmppsAuthApiExtension::class)
class RecordIntegrationTest : IntegrationTestBase() {

  @BeforeEach
  fun clearDB() {
    integrationTestHelpers.clearDB()
    hmppsAuth.stubGrantToken()
    generalLedgerApi.resetAll()
  }

  @Nested
  inner class PostAdvanceRecord {
    val prisonId = "LEI"
    val prisonNumber = "A1234BC"
    val idempotencyKey = UUID.randomUUID()

    val prisonerParentAccountId = UUID.randomUUID()
    val prisonerSubAccountId = UUID.randomUUID()

    val prisonParentAccountId = UUID.randomUUID()
    val prisonSubAccountId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
      generalLedgerApi.stubGetSubAccount(
        parentReference = prisonId,
        subAccountReference = "1502:ADV",
        subAccountID = prisonSubAccountId,
        parentAccountId = prisonParentAccountId,
      )

      generalLedgerApi.stubGetSubAccount(
        parentReference = prisonNumber,
        subAccountReference = "SPENDS",
        subAccountID = prisonerSubAccountId,
        parentAccountId = prisonerParentAccountId,
      )
    }

    @Test
    fun `should return 201 and the created record`() {
      val advanceRecordRequest = CreateAdvanceRecordRequest(
        legacyPaymentProfileId = 1234,
        legacyInformationNumber = "5678",
        prisonNumber = prisonNumber,
        prisonID = prisonId,
        amount = 10,
        createdOn = Instant.now(),
        repaymentStartDate = Instant.now(),
        repaymentAmount = 1,
        reference = "REF",
        createdBy = "USER",
        status = AdvanceStatus.ACTIVE,
        legacyTransactionId = 123,
      )

      generalLedgerApi.stubPostTransaction(
        creditorSubAccountUuid = prisonerSubAccountId.toString(),
        debtorSubAccountUuid = prisonSubAccountId.toString(),
        returnUUID = UUID.randomUUID(),
        amount = advanceRecordRequest.amount,
        legacyTransactionId = advanceRecordRequest.legacyTransactionId.toString(),
      )

      val responseBody = webTestClient.post().uri("/advances")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .headers(setIdempotencyKey(idempotencyKey))
        .bodyValue(advanceRecordRequest)
        .exchange()
        .expectStatus()
        .isCreated
        .expectBody<AdvanceRecordResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.id).isNotNull
      assertThat(responseBody.legacyInformationNumber).isEqualTo(advanceRecordRequest.legacyInformationNumber)
      generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/transactions")))
    }

    @Test
    fun `should return 201 if the payment profile id already exists`() {
      val advanceTime = Instant.now().truncatedTo(ChronoUnit.MILLIS)
      val advanceRecordRequest = CreateAdvanceRecordRequest(
        legacyPaymentProfileId = 1234,
        legacyInformationNumber = "5678",
        prisonNumber = prisonNumber,
        prisonID = prisonId,
        amount = 10,
        createdOn = advanceTime,
        repaymentStartDate = advanceTime,
        repaymentAmount = 1,
        reference = "REF",
        createdBy = "USER",
        status = AdvanceStatus.ACTIVE,
        legacyTransactionId = 123,
      )

      generalLedgerApi.stubPostTransaction(
        creditorSubAccountUuid = prisonerSubAccountId.toString(),
        debtorSubAccountUuid = prisonSubAccountId.toString(),
        returnUUID = UUID.randomUUID(),
        amount = advanceRecordRequest.amount,
        legacyTransactionId = advanceRecordRequest.legacyTransactionId.toString(),
      )

      val responseBody1 = webTestClient.post().uri("/advances")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .headers(setIdempotencyKey(idempotencyKey))
        .bodyValue(advanceRecordRequest)
        .exchange()
        .expectStatus()
        .isCreated
        .expectBody<AdvanceRecordResponse>()
        .returnResult()
        .responseBody!!

      val responseBody2 = webTestClient.post().uri("/advances")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .headers(setIdempotencyKey(idempotencyKey))
        .bodyValue(advanceRecordRequest)
        .exchange()
        .expectStatus()
        .isCreated
        .expectBody<AdvanceRecordResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody1).isEqualTo(responseBody2)
      generalLedgerApi.verify(2, postRequestedFor(urlPathMatching("/transactions")))
    }

    @Test
    fun `should return 400 bad request if Idempotency-Key is missing`() {
      val advanceRecordRequest = CreateAdvanceRecordRequest(
        legacyPaymentProfileId = 1234,
        legacyInformationNumber = "5678",
        prisonNumber = prisonNumber,
        prisonID = prisonId,
        amount = 10,
        createdOn = Instant.now(),
        repaymentStartDate = Instant.now(),
        repaymentAmount = 1,
        reference = "REF",
        createdBy = "USER",
        status = AdvanceStatus.ACTIVE,
        legacyTransactionId = 123,
      )

      webTestClient.post().uri("/advances")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .bodyValue(advanceRecordRequest)
        .exchange()
        .expectStatus()
        .isBadRequest
        .expectBody<ErrorResponse>()
    }

    @Test
    fun `should return 400 bad request when sent a malformed body`() {
      val jsonBody = """{ "bad": "json" }"""

      webTestClient.post().uri("/advances")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .headers(setIdempotencyKey(idempotencyKey))
        .header("Content-Type", "application/json")
        .bodyValue(jsonBody)
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `should return 403 forbidden when using the incorrect role`() {
      val advanceRecordRequest = CreateAdvanceRecordRequest(
        legacyPaymentProfileId = 1234,
        legacyInformationNumber = "5678",
        prisonNumber = prisonNumber,
        prisonID = prisonId,
        amount = 10,
        createdOn = Instant.now(),
        repaymentStartDate = Instant.now(),
        repaymentAmount = 1,
        reference = "REF",
        createdBy = "USER",
        status = AdvanceStatus.ACTIVE,
      )

      webTestClient.post().uri("/advances")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RO)))
        .headers(setIdempotencyKey(idempotencyKey))
        .bodyValue(advanceRecordRequest)
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `should return 502 when general ledger return an error`() {
      val advanceRecordRequest = CreateAdvanceRecordRequest(
        legacyPaymentProfileId = 1234,
        legacyInformationNumber = "5678",
        prisonNumber = prisonNumber,
        prisonID = prisonId,
        amount = 10,
        createdOn = Instant.now(),
        repaymentStartDate = Instant.now(),
        repaymentAmount = 1,
        reference = "REF",
        createdBy = "USER",
        status = AdvanceStatus.ACTIVE,
        legacyTransactionId = 123,
      )

      generalLedgerApi.stubPostTransactionReturnsInternalServerError()

      webTestClient.post().uri("/advances")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .headers(setIdempotencyKey(idempotencyKey))
        .bodyValue(advanceRecordRequest)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY_502)
        .expectBody<ErrorResponse>()

      generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/transactions")))
    }
  }

  @Nested
  inner class WriteOffAdvance {

    val prisonNumber = "A1234AA"
    val prisonId = "LEI"
    val prisonerSubAccountId: UUID = UUID.randomUUID()
    val prisonSubAccountId: UUID = UUID.randomUUID()
    val prisonParentAccountId: UUID = UUID.randomUUID()
    val prisonerParentAccountId: UUID = UUID.randomUUID()

    @Test
    fun `should return 200 when an advance has been successfully written off`() {
      val advanceCreated = integrationTestHelpers.createAdvance(
        prisonNumber = prisonNumber,
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

      val advanceWriteOffRequest = AdvanceWriteOffRequest(
        writeOffDateTime = Instant.now(),
        writtenOffBy = "TEST",
      )

      val response = webTestClient.post().uri("/advances/${advanceCreated.id}/write-off")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .bodyValue(advanceWriteOffRequest)
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<AdvanceRecordResponse>()
        .returnResult()
        .responseBody!!

      assertThat(response.status).isEqualTo(AdvanceStatus.WRITTEN_OFF)
      assertThat(response.id).isEqualTo(advanceCreated.id)
    }

    @ParameterizedTest
    @CsvSource(
      "REPAID",
      "WRITTEN_OFF",
    )
    fun `should return 400 if the advance is written off already`(advanceStatus: AdvanceStatus) {
      val advanceCreated = integrationTestHelpers.createAdvance(
        prisonNumber = prisonNumber,
        legacyPaymentProfileId = 123,
        legacyInformationNumber = "1234",
        amount = 10,
        prisonId = prisonId,
        repaymentAmount = 5,
        status = advanceStatus,
        prisonerSubAccountId = prisonerSubAccountId,
        prisonSubAccountId = prisonSubAccountId,
        prisonParentAccountId = prisonParentAccountId,
        prisonerParentAccountId = prisonerParentAccountId,
      )

      val advanceWriteOffRequest = AdvanceWriteOffRequest(
        writeOffDateTime = Instant.now(),
        writtenOffBy = "TEST",
      )

      val error = webTestClient.post().uri("/advances/${advanceCreated.id}/write-off")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .header("Content-Type", "application/json")
        .bodyValue(advanceWriteOffRequest)
        .exchange()
        .expectStatus()
        .isBadRequest
        .expectBody<ErrorResponse>()
        .returnResult()
        .responseBody!!

      assertThat(error.status).isEqualTo(400)
      assertThat(error.userMessage).isEqualTo("Cannot write off an advance record that is not active")
    }

    @Test
    fun `should return 400 if the id is invalid`() {
      val advanceWriteOffRequest = AdvanceWriteOffRequest(
        writeOffDateTime = Instant.now(),
        writtenOffBy = "TEST",
      )

      val error = webTestClient.post().uri("/advances/INVALID/write-off")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .header("Content-Type", "application/json")
        .bodyValue(advanceWriteOffRequest)
        .exchange()
        .expectStatus()
        .isBadRequest
        .expectBody<ErrorResponse>()
        .returnResult()
        .responseBody!!

      assertThat(error.status).isEqualTo(400)
      assertThat(error.userMessage).isEqualTo("Parameter 'advanceId' must be of type UUID")
    }

    @Test
    fun `should return 400 if the body is invalid`() {
      val error = webTestClient.post().uri("/advances/${UUID.randomUUID()}/write-off")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .header("Content-Type", "application/json")
        .bodyValue("{\"hello\": \"world\"}")
        .exchange()
        .expectStatus()
        .isBadRequest
        .expectBody<ErrorResponse>()
        .returnResult()
        .responseBody!!

      assertThat(error.status).isEqualTo(400)
      assertThat(error.userMessage).contains("Validation failure: JSON parse error")
    }

    @ParameterizedTest
    @CsvSource(
      "INVALID_ROLE",
      ROLE_PRISONER_FINANCE__ADVANCES__RO,
    )
    fun `should return 403 forbidden when using the incorrect role`(role: String) {
      val advanceWriteOffRequest = AdvanceWriteOffRequest(
        writeOffDateTime = Instant.now(),
        writtenOffBy = "TEST",
      )
      webTestClient.post().uri("/advances/${UUID.randomUUID()}/write-off")
        .headers(setAuthorisation(roles = listOf(role)))
        .header("Content-Type", "application/json")
        .bodyValue(advanceWriteOffRequest)
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `should return 404 when the advance does not exist`() {
      val advanceWriteOffRequest = AdvanceWriteOffRequest(
        writeOffDateTime = Instant.now(),
        writtenOffBy = "TEST",
      )
      val response = webTestClient.post().uri("/advances/${UUID.randomUUID()}/write-off")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .header("Content-Type", "application/json")
        .bodyValue(advanceWriteOffRequest)
        .exchange()
        .expectStatus().isNotFound
        .expectBody<ErrorResponse>()
        .returnResult()
        .responseBody!!

      assertThat(response.status).isEqualTo(404)
      assertThat(response.userMessage).isEqualTo("Advance record not found")
    }
  }
}
