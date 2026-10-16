package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.repositories

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration.ContainersConfig
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.entities.AdvanceRecordEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.enums.AdvanceStatus
import java.time.Instant
import java.util.UUID

@Import(ContainersConfig::class)
@DataJpaTest
class AdvanceRecordRepositoryTest(@Autowired val advanceRecordRepository: AdvanceRecordRepository) {

  @BeforeEach
  fun setup() {
    advanceRecordRepository.deleteAll()
  }

  val prisonNumber = "A1234AA"

  @EnumSource(AdvanceStatus::class)
  @ParameterizedTest(name = "should return an empty list when nothing exists for status {0}")
  fun `should return an empty list when nothing exists`(status: AdvanceStatus) {
    val results = advanceRecordRepository
      .findAdvanceRecordEntitiesByPrisonNumberAndStatus(prisonNumber, status)
    assert(results.isEmpty())
  }

  private fun seedAdvances(activeAmount: Int, repaidAmount: Int, writtenOffAmount: Int) {
    var legacyPaymentProfileId = 123456L

    repeat(activeAmount) {
      legacyPaymentProfileId += 1

      val advanceRecord = AdvanceRecordEntity(
        id = UUID.randomUUID(),
        legacyPaymentProfileId = legacyPaymentProfileId,
        legacyInformationNumber = "789",
        prisonNumber = prisonNumber,
        prisonID = "LEI",
        amount = 10,
        createdOn = Instant.now(),
        repaymentStartDate = Instant.now(),
        repaymentAmount = 10,
        reference = "REF",
        comment = "COMMENT",
        createdBy = "USER",
        updatedAt = Instant.now(),
        status = AdvanceStatus.ACTIVE,
      )

      advanceRecordRepository.save(advanceRecord)
    }

    repeat(repaidAmount) {
      legacyPaymentProfileId += 1

      val advanceRecord = AdvanceRecordEntity(
        id = UUID.randomUUID(),
        legacyPaymentProfileId = legacyPaymentProfileId,
        legacyInformationNumber = "789",
        prisonNumber = prisonNumber,
        prisonID = "LEI",
        amount = 10,
        createdOn = Instant.now(),
        repaymentStartDate = Instant.now(),
        repaymentAmount = 10,
        reference = "REF",
        comment = "COMMENT",
        createdBy = "USER",
        updatedAt = Instant.now(),
        status = AdvanceStatus.REPAID,
      )

      advanceRecordRepository.save(advanceRecord)
    }

    repeat(writtenOffAmount) {
      legacyPaymentProfileId += 1

      val advanceRecord = AdvanceRecordEntity(
        id = UUID.randomUUID(),
        legacyPaymentProfileId = legacyPaymentProfileId,
        legacyInformationNumber = "789",
        prisonNumber = prisonNumber,
        prisonID = "LEI",
        amount = 10,
        createdOn = Instant.now(),
        repaymentStartDate = Instant.now(),
        repaymentAmount = 10,
        reference = "REF",
        comment = "COMMENT",
        createdBy = "USER",
        updatedAt = Instant.now(),
        status = AdvanceStatus.WRITTEN_OFF,
      )

      advanceRecordRepository.save(advanceRecord)
    }
  }

  @Test
  fun `should return the correct amount of advance records`() {
    val activeAmount = 1
    val repaidAmount = 2
    val writtenOffAmount = 3

    seedAdvances(activeAmount = activeAmount, repaidAmount = repaidAmount, writtenOffAmount = writtenOffAmount)

    val advanceForAnotherPrisoner = AdvanceRecordEntity(
      id = UUID.randomUUID(),
      legacyPaymentProfileId = 9999,
      legacyInformationNumber = "789",
      prisonNumber = "X1234XX",
      prisonID = "LEI",
      amount = 10,
      createdOn = Instant.now(),
      repaymentStartDate = Instant.now(),
      repaymentAmount = 10,
      reference = "REF",
      comment = "COMMENT",
      createdBy = "USER",
      updatedAt = Instant.now(),
      status = AdvanceStatus.REPAID,
    )

    advanceRecordRepository.save(advanceForAnotherPrisoner)

    assertThat(advanceRecordRepository.findAdvanceRecordEntitiesByPrisonNumberAndStatus(prisonNumber, AdvanceStatus.ACTIVE).size).isEqualTo(activeAmount)
    assertThat(advanceRecordRepository.findAdvanceRecordEntitiesByPrisonNumberAndStatus(prisonNumber, AdvanceStatus.REPAID).size).isEqualTo(repaidAmount)
    assertThat(advanceRecordRepository.findAdvanceRecordEntitiesByPrisonNumberAndStatus(prisonNumber, AdvanceStatus.WRITTEN_OFF).size).isEqualTo(writtenOffAmount)
  }
}
