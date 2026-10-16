package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.config.ROLE_PRISONER_FINANCE__ADVANCES__RW
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.entities.AdvanceRecordEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.enums.AdvanceStatus
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.repositories.AdvanceRecordRepository
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.Instant
import java.util.UUID

class AdvanceBalancesTest : IntegrationTestBase() {

  @Autowired lateinit var advanceRecordRepository: AdvanceRecordRepository

  @Nested
  inner class AdvanceBalance {
    val advanceAmount = 1000L
    val prisonParentAccountId = UUID.randomUUID()
    val prisonerParentAccountId = UUID.randomUUID()
    val prisonSubAccountId = UUID.randomUUID()
    val prisonerSubAccountId = UUID.randomUUID()
    lateinit var advanceBalanceResponse: AdvanceBalanceResponse

    @BeforeEach
    fun clearDB() {
      integrationTestHelpers.clearDB()
      hmppsAuth.stubGrantToken()
      generalLedgerApi.resetAll()
    }

    @Test
    fun `Should return the balance of a partially repaid advance`() {
      val repaymentAmount = 50L
      val numOfRepayments = 7

      val advanceResponse = integrationTestHelpers.createAdvance(
        prisonNumber = "A1234AB",
        legacyPaymentProfileId = 123,
        legacyInformationNumber = "12312",
        amount = advanceAmount,
        prisonId = "LEI",
        repaymentAmount = repaymentAmount,
        status = AdvanceStatus.ACTIVE,
        prisonerSubAccountId = prisonerSubAccountId,
        prisonSubAccountId = prisonSubAccountId,
        prisonParentAccountId = prisonParentAccountId,
        prisonerParentAccountId = prisonerParentAccountId,
      )

      repeat(numOfRepayments) {
        integrationTestHelpers.repayAdvance(
          advanceId = advanceResponse.id,
          repaymentAmount = repaymentAmount,
          prisonSubAccountId = prisonSubAccountId,
          prisonerSubAccountId = prisonerSubAccountId,
        )
      }

      advanceBalanceResponse = webTestClient.get().uri("/advances/${advanceResponse.id}/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<AdvanceBalanceResponse>()
        .returnResult()
        .responseBody!!

      assertThat(advanceBalanceResponse.balanceOutstanding).isEqualTo(650)
      assertThat(advanceBalanceResponse.advanceAmount).isEqualTo(advanceAmount)
      assertThat(advanceBalanceResponse.remainingPayments).isEqualTo(13)
      assertThat(advanceBalanceResponse.weeklyPaymentAmount).isEqualTo(repaymentAmount)
    }

    @Test
    fun `Should return the balance of an advance that was never repaid`() {
      val repaymentAmount = 50L

      val advanceResponse = integrationTestHelpers.createAdvance(
        prisonNumber = "A1234AB",
        legacyPaymentProfileId = 123,
        legacyInformationNumber = "12312",
        amount = advanceAmount,
        prisonId = "LEI",
        repaymentAmount = repaymentAmount,
        status = AdvanceStatus.ACTIVE,
        prisonerSubAccountId = prisonerSubAccountId,
        prisonSubAccountId = prisonSubAccountId,
        prisonParentAccountId = prisonParentAccountId,
        prisonerParentAccountId = prisonerParentAccountId,
      )

      advanceBalanceResponse = webTestClient.get().uri("/advances/${advanceResponse.id}/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<AdvanceBalanceResponse>()
        .returnResult()
        .responseBody!!

      assertThat(advanceBalanceResponse.balanceOutstanding).isEqualTo(advanceAmount)
      assertThat(advanceBalanceResponse.advanceAmount).isEqualTo(advanceAmount)
      assertThat(advanceBalanceResponse.remainingPayments).isEqualTo(20)
      assertThat(advanceBalanceResponse.weeklyPaymentAmount).isEqualTo(repaymentAmount)
    }

    @Test
    fun `Should return the balance of an advance that is fully repaid`() {
      val numOfRepayments = 20
      val repaymentAmount = 50L

      val advanceResponse = integrationTestHelpers.createAdvance(
        prisonNumber = "A1234AB",
        legacyPaymentProfileId = 123,
        legacyInformationNumber = "12312",
        amount = advanceAmount,
        prisonId = "LEI",
        repaymentAmount = repaymentAmount,
        status = AdvanceStatus.ACTIVE,
        prisonerSubAccountId = prisonerSubAccountId,
        prisonSubAccountId = prisonSubAccountId,
        prisonParentAccountId = prisonParentAccountId,
        prisonerParentAccountId = prisonerParentAccountId,
      )

      repeat(numOfRepayments) {
        integrationTestHelpers.repayAdvance(
          advanceId = advanceResponse.id,
          repaymentAmount = repaymentAmount,
          prisonSubAccountId = prisonSubAccountId,
          prisonerSubAccountId = prisonerSubAccountId,
        )
      }

      advanceBalanceResponse = webTestClient.get().uri("/advances/${advanceResponse.id}/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<AdvanceBalanceResponse>()
        .returnResult()
        .responseBody!!

      assertThat(advanceBalanceResponse.balanceOutstanding).isEqualTo(0)
      assertThat(advanceBalanceResponse.advanceAmount).isEqualTo(advanceAmount)
      assertThat(advanceBalanceResponse.remainingPayments).isEqualTo(0)
      assertThat(advanceBalanceResponse.weeklyPaymentAmount).isEqualTo(repaymentAmount)
    }

    @Test
    fun `Should return the balance of an advance that is written-off`() {
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

      val advanceBalanceResponse = webTestClient.get().uri("/advances/${advanceRecordEntity.id}/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<AdvanceBalanceResponse>()
        .returnResult()
        .responseBody!!

      assertThat(advanceBalanceResponse.balanceOutstanding).isEqualTo(0)
      assertThat(advanceBalanceResponse.advanceAmount).isEqualTo(advanceRecordEntity.amount)
      assertThat(advanceBalanceResponse.remainingPayments).isEqualTo(0)
      assertThat(advanceBalanceResponse.weeklyPaymentAmount).isEqualTo(advanceRecordEntity.repaymentAmount)
    }

    @Test
    fun `Should return 400 if the advanceId is not a UUID`() {
      val error = webTestClient.get().uri("/advances/INVALID/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
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
    fun `Should return 403 an invalid role is passed`() {
      webTestClient.get().uri("/advances/${UUID.randomUUID()}/balance")
        .headers(setAuthorisation(roles = listOf("ROLE__INVALID")))
        .exchange()
        .expectStatus()
        .isForbidden
        .expectBody<ErrorResponse>()
    }

    @Test
    fun `Should return 404 if the advance does not exist`() {
      val error = webTestClient.get().uri("/advances/${UUID.randomUUID()}/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .exchange()
        .expectStatus()
        .isNotFound
        .expectBody<ErrorResponse>()
        .returnResult()
        .responseBody!!

      assertThat(error.status).isEqualTo(404)
      assertThat(error.userMessage).isEqualTo("Advance record not found")
    }
  }
}
