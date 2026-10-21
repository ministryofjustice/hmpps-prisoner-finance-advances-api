package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.config.ROLE_PRISONER_FINANCE__ADVANCES__RO
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.config.ROLE_PRISONER_FINANCE__ADVANCES__RW
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.enums.AdvanceStatus
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AccountAdvanceBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.repositories.AdvanceRecordRepository
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    val advanceCreatedDate = LocalDate.parse("20/10/2026", DateTimeFormatter.ofPattern("dd/MM/yyyy")).atStartOfDay(ZoneId.of("Europe/London")).toInstant()
    val expectedNextPaymentDate = LocalDate.parse("27/10/2026", DateTimeFormatter.ofPattern("dd/MM/yyyy"))

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
        createdOn = advanceCreatedDate,
        repaymentStartDate = advanceCreatedDate,
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
      assertThat(advanceBalanceResponse.nextPaymentDate).isEqualTo(expectedNextPaymentDate)
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
        createdOn = advanceCreatedDate,
        repaymentStartDate = advanceCreatedDate,
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
      assertThat(advanceBalanceResponse.nextPaymentDate).isEqualTo(expectedNextPaymentDate)
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
        createdOn = advanceCreatedDate,
        repaymentStartDate = advanceCreatedDate,
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
      assertThat(advanceBalanceResponse.nextPaymentDate).isNull()
    }

    @Test
    fun `Should return the balance of an advance that is fully written-off`() {
      val advanceResponse = integrationTestHelpers.createAdvance(
        prisonNumber = "A1234AB",
        legacyPaymentProfileId = 123,
        legacyInformationNumber = "12312",
        amount = advanceAmount,
        prisonId = "LEI",
        repaymentAmount = 1,
        status = AdvanceStatus.ACTIVE,
        prisonerSubAccountId = prisonerSubAccountId,
        prisonSubAccountId = prisonSubAccountId,
        prisonParentAccountId = prisonParentAccountId,
        prisonerParentAccountId = prisonerParentAccountId,
      )

      integrationTestHelpers.writeOffAdvance(
        advanceId = advanceResponse.id,
        amount = advanceResponse.amount,
      )

      val advanceBalanceResponse = webTestClient.get().uri("/advances/${advanceResponse.id}/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<AdvanceBalanceResponse>()
        .returnResult()
        .responseBody!!

      assertThat(advanceBalanceResponse.balanceOutstanding).isEqualTo(0)
      assertThat(advanceBalanceResponse.advanceAmount).isEqualTo(advanceResponse.amount)
      assertThat(advanceBalanceResponse.remainingPayments).isEqualTo(0)
      assertThat(advanceBalanceResponse.weeklyPaymentAmount).isEqualTo(advanceResponse.repaymentAmount)
      assertThat(advanceBalanceResponse.nextPaymentDate).isNull()
    }

    @Test
    fun `Should return the balance of an advance that is partially written-off`() {
      val advanceResponse = integrationTestHelpers.createAdvance(
        prisonNumber = "A1234AB",
        legacyPaymentProfileId = 123,
        legacyInformationNumber = "12312",
        amount = 100,
        prisonId = "LEI",
        repaymentAmount = 10,
        status = AdvanceStatus.ACTIVE,
        prisonerSubAccountId = prisonerSubAccountId,
        prisonSubAccountId = prisonSubAccountId,
        prisonParentAccountId = prisonParentAccountId,
        prisonerParentAccountId = prisonerParentAccountId,
        createdOn = advanceCreatedDate,
        repaymentStartDate = advanceCreatedDate,
      )

      integrationTestHelpers.writeOffAdvance(
        advanceId = advanceResponse.id,
        amount = 10,
      )

      val advanceBalanceResponse = webTestClient.get().uri("/advances/${advanceResponse.id}/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<AdvanceBalanceResponse>()
        .returnResult()
        .responseBody!!

      assertThat(advanceBalanceResponse.balanceOutstanding).isEqualTo(90)
      assertThat(advanceBalanceResponse.advanceAmount).isEqualTo(advanceResponse.amount)
      assertThat(advanceBalanceResponse.remainingPayments).isEqualTo(9)
      assertThat(advanceBalanceResponse.weeklyPaymentAmount).isEqualTo(advanceResponse.repaymentAmount)
      assertThat(advanceBalanceResponse.nextPaymentDate).isEqualTo(expectedNextPaymentDate)
    }

    @Test
    fun `Should return the balance of an advance that is partially written-off and partially repaid`() {
      val advanceResponse = integrationTestHelpers.createAdvance(
        prisonNumber = "A1234AB",
        legacyPaymentProfileId = 123,
        legacyInformationNumber = "12312",
        amount = 100,
        prisonId = "LEI",
        repaymentAmount = 5,
        status = AdvanceStatus.ACTIVE,
        prisonerSubAccountId = prisonerSubAccountId,
        prisonSubAccountId = prisonSubAccountId,
        prisonParentAccountId = prisonParentAccountId,
        prisonerParentAccountId = prisonerParentAccountId,
        createdOn = advanceCreatedDate,
        repaymentStartDate = advanceCreatedDate,
      )

      integrationTestHelpers.repayAdvance(
        advanceId = advanceResponse.id,
        repaymentAmount = advanceResponse.repaymentAmount,
        prisonSubAccountId = prisonSubAccountId,
        prisonerSubAccountId = prisonerSubAccountId,
      )

      integrationTestHelpers.writeOffAdvance(
        advanceId = advanceResponse.id,
        amount = 10,
      )

      val advanceBalanceResponse = webTestClient.get().uri("/advances/${advanceResponse.id}/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<AdvanceBalanceResponse>()
        .returnResult()
        .responseBody!!

      assertThat(advanceBalanceResponse.balanceOutstanding).isEqualTo(85)
      assertThat(advanceBalanceResponse.advanceAmount).isEqualTo(advanceResponse.amount)
      assertThat(advanceBalanceResponse.remainingPayments).isEqualTo(17)
      assertThat(advanceBalanceResponse.weeklyPaymentAmount).isEqualTo(advanceResponse.repaymentAmount)
      assertThat(advanceBalanceResponse.nextPaymentDate).isEqualTo(expectedNextPaymentDate)
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

  @Nested
  inner class AccountBalance {

    @BeforeEach
    fun clearDB() {
      integrationTestHelpers.clearDB()
      hmppsAuth.stubGrantToken()
      generalLedgerApi.resetAll()
    }

    val prisonNumber = "A1234BC"
    val prisonId = "LEI"
    val prisonerSubAccountId = UUID.randomUUID()
    val prisonSubAccountId = UUID.randomUUID()
    val prisonParentAccountId = UUID.randomUUID()
    val prisonerParentAccountId = UUID.randomUUID()

    @Test
    fun `should return 200 and aggregated view of all the advances`() {
      val advanceOne = integrationTestHelpers.createAdvance(
        prisonNumber = prisonNumber,
        legacyPaymentProfileId = 12345L,
        legacyInformationNumber = "6789",
        amount = 500,
        prisonId = prisonId,
        repaymentAmount = 50,
        status = AdvanceStatus.ACTIVE,
        prisonerSubAccountId = prisonerSubAccountId,
        prisonSubAccountId = prisonSubAccountId,
        prisonParentAccountId = prisonParentAccountId,
        prisonerParentAccountId = prisonerParentAccountId,
      )

      val advanceTwo = integrationTestHelpers.createAdvance(
        prisonNumber = prisonNumber,
        legacyPaymentProfileId = 6789L,
        legacyInformationNumber = "6789-1",
        amount = 1000,
        prisonId = prisonId,
        repaymentAmount = 100,
        status = AdvanceStatus.ACTIVE,
        prisonerSubAccountId = prisonerSubAccountId,
        prisonSubAccountId = prisonSubAccountId,
        prisonParentAccountId = prisonParentAccountId,
        prisonerParentAccountId = prisonerParentAccountId,
      )

      val advanceRepaymentResponse = integrationTestHelpers.repayAdvance(
        advanceOne.id,
        repaymentAmount = 50,
        prisonSubAccountId = prisonSubAccountId,
        prisonerSubAccountId = prisonerSubAccountId,
      )

      val response = webTestClient.get().uri("/advances/account/$prisonNumber/balance", prisonNumber)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RO)))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<AccountAdvanceBalanceResponse>()
        .returnResult()
        .responseBody!!

      assertThat(response.weeklyPaymentAmount).isEqualTo(advanceOne.repaymentAmount + advanceTwo.repaymentAmount)
      assertThat(response.advancesAmount).isEqualTo(advanceOne.amount + advanceTwo.amount)
      assertThat(response.balancesOutstanding).isEqualTo(advanceOne.amount + advanceTwo.amount - advanceRepaymentResponse.amount)
    }

    @Test
    fun `should return 200 and zeros when no active advances exist`() {
      val response = webTestClient.get().uri("/advances/account/$prisonNumber/balance", prisonNumber)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RO)))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<AccountAdvanceBalanceResponse>()
        .returnResult()
        .responseBody!!

      assertThat(response.weeklyPaymentAmount).isEqualTo(0)
      assertThat(response.advancesAmount).isEqualTo(0)
      assertThat(response.balancesOutstanding).isEqualTo(0)
    }

    @Test
    fun `should return 400 if the prisonNumber is not valid`() {
      webTestClient.get().uri("/advances/account/INVALID/balance", "INVALID")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RO)))
        .exchange()
        .expectStatus()
        .isBadRequest
        .expectBody<ErrorResponse>()
        .returnResult()
        .responseBody!!
    }

    @Test
    fun `should return 403 an invalid role is passed`() {
      webTestClient.get().uri("/advances/account/$prisonNumber/balance", prisonNumber)
        .headers(setAuthorisation(roles = listOf("ROLE__INVALID")))
        .exchange()
        .expectStatus()
        .isForbidden
        .expectBody<ErrorResponse>()
    }
  }
}
