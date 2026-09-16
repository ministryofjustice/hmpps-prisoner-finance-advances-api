package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.services

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.entities.AdvanceRecord
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.request.CreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.PagedResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.repositories.AdvanceRecordRepository
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.utils.toPageResponse
import java.util.UUID

@Service
class RecordService(private val advanceRecordRepository: AdvanceRecordRepository) {
  fun createAdvanceRecord(createAdvanceRecordRequest: CreateAdvanceRecordRequest): AdvanceRecordResponse {
    val advanceRecordEntity = AdvanceRecord(
      id = UUID.randomUUID(),
      legacyPaymentProfileId = createAdvanceRecordRequest.legacyPaymentProfileId,
      legacyInformationNumber = createAdvanceRecordRequest.legacyInformationNumber,
      prisonNumber = createAdvanceRecordRequest.prisonNumber,
      prisonID = createAdvanceRecordRequest.prisonID,
      amount = createAdvanceRecordRequest.amount,
      createdOn = createAdvanceRecordRequest.createdOn,
      repaymentStartDate = createAdvanceRecordRequest.repaymentStartDate,
      repaymentAmount = createAdvanceRecordRequest.repaymentAmount,
      reference = createAdvanceRecordRequest.reference,
      createdBy = createAdvanceRecordRequest.createdBy,
      status = createAdvanceRecordRequest.status,
    )
    try {
      val createdRecord = advanceRecordRepository.save(advanceRecordEntity)
      return AdvanceRecordResponse.fromEntity(createdRecord)
    } catch (e: Exception) {
      val isDuplicateHold = e.message?.contains("uc_advance_records_legacy_payment_profile_id") == true
      if (e is DataIntegrityViolationException && isDuplicateHold) {
        val previouslyCreatedAdvancedRecord = advanceRecordRepository.getAdvanceRecordsByLegacyPaymentProfileId(createAdvanceRecordRequest.legacyPaymentProfileId)
        return AdvanceRecordResponse.fromEntity(previouslyCreatedAdvancedRecord!!)
      }
      throw e
    }
  }

  fun getAdvances(prisonNumber: String, pageSize: Int, pageNumber: Int): PagedResponse<AdvanceRecordResponse> {
    val zeroIndexedPage: Int = pageNumber - 1

    val pagedRequest = PageRequest.of(
      zeroIndexedPage,
      pageSize,
      Sort.by(
        Sort.Order.desc("createdOn"),
        Sort.Order.desc("id"),
      ),
    )

    val result = advanceRecordRepository.findByPrisonNumber(prisonNumber, pagedRequest)

    return result.toPageResponse { content ->
      content.map { AdvanceRecordResponse.fromEntity(it) }
    }
  }
}
