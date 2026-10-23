package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.config.ROLE_PRISONER_FINANCE__ADVANCES__RW
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.enums.AdvanceStatus
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.generalledger.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.request.AdvanceMigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceRecordResponse
import java.time.Instant
import java.util.UUID

class AdvanceMigrationTest : IntegrationTestBase() {
  val migrationRequest = AdvanceMigrationRequest(
    legacyPaymentProfileId = 1111,
    legacyInformationNumber = "1234-1",
    prisonNumber = "A134ABC",
    prisonID = "LEI",
    amountIssued = 100L,
    amountOutstanding = 80L,
    createdOn = Instant.now(),
    migrationTimestamp = Instant.now(),
    repaymentStartDate = Instant.now(),
    repaymentAmount = 10L,
    reference = "",
    comment = "",
    createdBy = "TEST",
    status = AdvanceStatus.ACTIVE,
    legacyTransactionId = 12345,
  )

  @Test
  fun `Should return 201 and advance record response on successful migration`() {
    val advanceResponse = webTestClient.post().uri("/migrate/advances")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
      .headers(setIdempotencyKey(UUID.randomUUID()))
      .bodyValue(migrationRequest)
      .exchange()
      .expectStatus()
      .isEqualTo(HttpStatus.CREATED)
      .expectBody<AdvanceRecordResponse>().returnResult().responseBody!!

    assertThat(advanceResponse.status).isEqualTo(migrationRequest.status)
    assertThat(advanceResponse.amount).isEqualTo(migrationRequest.amountIssued)
    assertThat(advanceResponse.prisonNumber).isEqualTo(migrationRequest.prisonNumber)
    assertThat(advanceResponse.legacyPaymentProfileId).isEqualTo(migrationRequest.legacyPaymentProfileId)
    assertThat(advanceResponse.comment).isEqualTo(migrationRequest.comment)
    assertThat(advanceResponse.prisonID).isEqualTo(migrationRequest.prisonID)
    assertThat(advanceResponse.repaymentAmount).isEqualTo(migrationRequest.repaymentAmount)
    assertThat(advanceResponse.repaymentStartDate).isEqualTo(migrationRequest.repaymentStartDate)
    assertThat(advanceResponse.reference).isEqualTo(migrationRequest.reference)
    assertThat(advanceResponse.comment).isEqualTo(migrationRequest.comment)
    assertThat(advanceResponse.createdBy).isEqualTo(migrationRequest.createdBy)
    assertThat(advanceResponse.createdOn).isEqualTo(migrationRequest.createdOn)
    assertThat(advanceResponse.legacyInformationNumber).isEqualTo(migrationRequest.legacyInformationNumber)
    // TODO: we need to find out what this is
    // assertThat(advanceResponse.updatedAt).isEqualTo(Instant.now())
    assertThat(advanceResponse.generalLedgerUUID).isEqualTo(null)
  }

  @Disabled
  @Test
  fun `Should return the same advance record if called twice`() {
    val firstAdvanceResponse = webTestClient.post().uri("/migrate/advances")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
      .bodyValue(migrationRequest)
      .exchange()
      .expectStatus()
      .isEqualTo(HttpStatus.CREATED)
      .expectBody<AdvanceRecordResponse>().returnResult().responseBody!!

    val secondAdvanceResponse = webTestClient.post().uri("/migrate/advances")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
      .bodyValue(migrationRequest)
      .exchange()
      .expectStatus()
      .isEqualTo(HttpStatus.CREATED)
      .expectBody<AdvanceRecordResponse>().returnResult().responseBody!!

    assertThat(firstAdvanceResponse).isEqualTo(secondAdvanceResponse).usingRecursiveComparison()
  }

  @Test
  fun `Should return 403 forbidden when using the incorrect role`() {
    webTestClient.post().uri("/migrate/advances")
      .headers(setAuthorisation(roles = listOf("WRONG_ROLE")))
      .bodyValue(migrationRequest)
      .exchange()
      .expectStatus()
      .isEqualTo(HttpStatus.FORBIDDEN)
      .expectBody<ErrorResponse>()
  }
}
