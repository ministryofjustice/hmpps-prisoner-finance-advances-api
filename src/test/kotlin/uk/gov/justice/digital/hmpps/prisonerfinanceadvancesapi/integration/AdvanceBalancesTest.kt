package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.config.ROLE_PRISONER_FINANCE__ADVANCES__RW
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.enums.AdvanceStatus
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceRecordResponse
import java.util.UUID

class AdvanceBalancesTest: IntegrationTestBase() {


  @Nested
  inner class AdvanceBalance {

    val advanceAmount = 1000L
    val weeklyPayments = 50L
    val prisonParentAccountId = UUID.randomUUID()
    val prisonerParentAccountId = UUID.randomUUID()
    val prisonSubAccountId = UUID.randomUUID()
    val prisonerSubAccountId = UUID.randomUUID()
    lateinit var advanceResponse: AdvanceRecordResponse
    lateinit var advanceBalanceResponse: AdvanceBalanceResponse

    @Test
    fun `Should return the balance of a partially repaid advance`() {
      val numOfRepayments = 7

      advanceResponse = integrationTestHelpers.createAdvance(
        prisonNumber = "A1234AB",
        legacyPaymentProfileId = 123,
        legacyInformationNumber = "12312",
        amount = advanceAmount,
        prisonId = "LEI",
        repaymentAmount = weeklyPayments,
        status = AdvanceStatus.ACTIVE,
        prisonerSubAccountId = prisonerSubAccountId,
        prisonSubAccountId = prisonSubAccountId,
        prisonParentAccountId = prisonParentAccountId,
        prisonerParentAccountId = prisonerParentAccountId,
      )

      repeat(numOfRepayments) {
        integrationTestHelpers.repayAdvance(
          advanceId = advanceResponse.id,
          repaymentAmount = 1,
          prisonSubAccountId = prisonSubAccountId,
          prisonerSubAccountId = prisonerSubAccountId
        )
      }


      advanceBalanceResponse = webTestClient.get().uri("/advances/${advanceResponse.id}/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .exchange()
        .expectStatus()
        .isCreated
        .expectBody<AdvanceBalanceResponse>()
        .returnResult()
        .responseBody!!

      assertThat(advanceBalanceResponse.balanceOutstanding).isEqualTo(650)
      assertThat(advanceBalanceResponse.advanceAmount).isEqualTo(advanceAmount)
      assertThat(advanceBalanceResponse.remainingPayments).isEqualTo(13)
      assertThat(advanceBalanceResponse.weeklyPayments).isEqualTo(weeklyPayments)
    }

    @Test
    fun `Should return the balance of an advance that was never repaid`() {
      advanceResponse = integrationTestHelpers.createAdvance(
        prisonNumber = "A1234AB",
        legacyPaymentProfileId = 123,
        legacyInformationNumber = "12312",
        amount = advanceAmount,
        prisonId = "LEI",
        repaymentAmount = weeklyPayments,
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
        .isCreated
        .expectBody<AdvanceBalanceResponse>()
        .returnResult()
        .responseBody!!

      assertThat(advanceBalanceResponse.balanceOutstanding).isEqualTo(advanceAmount)
      assertThat(advanceBalanceResponse.advanceAmount).isEqualTo(advanceAmount)
      assertThat(advanceBalanceResponse.remainingPayments).isEqualTo(20)
      assertThat(advanceBalanceResponse.weeklyPayments).isEqualTo(weeklyPayments)
    }

    @Test
    fun `Should return the balance of an advance that is fully repaid`() {
      val numOfRepayments = 20

      advanceResponse = integrationTestHelpers.createAdvance(
        prisonNumber = "A1234AB",
        legacyPaymentProfileId = 123,
        legacyInformationNumber = "12312",
        amount = advanceAmount,
        prisonId = "LEI",
        repaymentAmount = weeklyPayments,
        status = AdvanceStatus.ACTIVE,
        prisonerSubAccountId = prisonerSubAccountId,
        prisonSubAccountId = prisonSubAccountId,
        prisonParentAccountId = prisonParentAccountId,
        prisonerParentAccountId = prisonerParentAccountId,
      )

      repeat(numOfRepayments) {
        integrationTestHelpers.repayAdvance(
          advanceId = advanceResponse.id,
          repaymentAmount = 1,
          prisonSubAccountId = prisonSubAccountId,
          prisonerSubAccountId = prisonerSubAccountId
        )
      }


      advanceBalanceResponse = webTestClient.get().uri("/advances/${advanceResponse.id}/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .exchange()
        .expectStatus()
        .isCreated
        .expectBody<AdvanceBalanceResponse>()
        .returnResult()
        .responseBody!!

      assertThat(advanceBalanceResponse.balanceOutstanding).isEqualTo(0)
      assertThat(advanceBalanceResponse.advanceAmount).isEqualTo(advanceAmount)
      assertThat(advanceBalanceResponse.remainingPayments).isEqualTo(0)
      assertThat(advanceBalanceResponse.weeklyPayments).isEqualTo(weeklyPayments)
    }
  }
}