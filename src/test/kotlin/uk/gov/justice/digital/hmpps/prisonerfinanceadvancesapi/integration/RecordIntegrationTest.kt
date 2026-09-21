package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.config.ROLE_PRISONER_FINANCE__ADVANCES__RO
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.config.ROLE_PRISONER_FINANCE__ADVANCES__RW
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.enums.AdvanceStatus
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.request.CreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceRecordResponse
import java.time.Instant
import java.time.temporal.ChronoUnit

class RecordIntegrationTest : IntegrationTestBase() {

  @BeforeEach
  fun clearDB() {
    integrationTestHelpers.clearDB()
  }

  @Nested
  inner class PostAdvanceRecord {

    @Test
    fun `should 201 and the created record`() {
      val advanceRecordRequest = CreateAdvanceRecordRequest(
        legacyPaymentProfileId = 1234,
        legacyInformationNumber = "5678",
        prisonNumber = "A1234BC",
        prisonID = "LEI",
        amount = 10,
        createdOn = Instant.now(),
        repaymentStartDate = Instant.now(),
        repaymentAmount = 1,
        reference = "REF",
        createdBy = "USER",
        status = AdvanceStatus.ACTIVE,
      )

      val responseBody = webTestClient.post().uri("/advances")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .bodyValue(advanceRecordRequest)
        .exchange()
        .expectStatus()
        .isCreated
        .expectBody<AdvanceRecordResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.id).isNotNull
      assertThat(responseBody.legacyInformationNumber).isEqualTo(advanceRecordRequest.legacyInformationNumber)
    }

    @Test
    fun `should 201 if the payment profile id already exists`() {
      val advanceTime = Instant.now().truncatedTo(ChronoUnit.MILLIS)

      val advanceRecordRequest = CreateAdvanceRecordRequest(
        legacyPaymentProfileId = 1234,
        legacyInformationNumber = "5678",
        prisonNumber = "A1234BC",
        prisonID = "LEI",
        amount = 10,
        createdOn = advanceTime,
        repaymentStartDate = advanceTime,
        repaymentAmount = 1,
        reference = "REF",
        createdBy = "USER",
        status = AdvanceStatus.ACTIVE,
      )

      val responseBody1 = webTestClient.post().uri("/advances")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .bodyValue(advanceRecordRequest)
        .exchange()
        .expectStatus()
        .isCreated
        .expectBody<AdvanceRecordResponse>()
        .returnResult()
        .responseBody!!

      val responseBody2 = webTestClient.post().uri("/advances")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .bodyValue(advanceRecordRequest)
        .exchange()
        .expectStatus()
        .isCreated
        .expectBody<AdvanceRecordResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody1).isEqualTo(responseBody2)
    }

    @Test
    fun `should return 400 bad request when sent a malformed body`() {
      val jsonBody = """{ "bad": "json" }"""

      webTestClient.post().uri("/advances")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
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
        prisonNumber = "A1234BC",
        prisonID = "LEI",
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
        .bodyValue(advanceRecordRequest)
        .exchange()
        .expectStatus().isForbidden
    }

    // TO-DO: Add 400, CONFLICT, Swagger Docs
  }
}
