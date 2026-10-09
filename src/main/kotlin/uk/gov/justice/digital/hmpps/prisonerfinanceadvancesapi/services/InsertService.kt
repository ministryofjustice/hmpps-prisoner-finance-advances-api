package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.services

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.entities.AdvancePaymentEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.entities.AdvanceRecordEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.repositories.AdvancePaymentRepository
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.repositories.AdvanceRecordRepository

@Service
class InsertService(
  private val advanceRecordRepository: AdvanceRecordRepository,
  private val advancePaymentRepository: AdvancePaymentRepository,
) {

  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = [Exception::class, Error::class])
  fun saveAdvanceAndPayment(
    advanceEntity: AdvanceRecordEntity,
    advancePaymentEntity: AdvancePaymentEntity,
  ): AdvanceRecordEntity {
    val savedRecord = advanceRecordRepository.saveAndFlush(advanceEntity)
    advancePaymentRepository.saveAndFlush(advancePaymentEntity)
    return savedRecord
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = [Exception::class, Error::class])
  fun saveAdvanceRepayment(
    advancePaymentEntity: AdvancePaymentEntity,
  ): AdvancePaymentEntity = advancePaymentRepository.saveAndFlush(advancePaymentEntity)
}
