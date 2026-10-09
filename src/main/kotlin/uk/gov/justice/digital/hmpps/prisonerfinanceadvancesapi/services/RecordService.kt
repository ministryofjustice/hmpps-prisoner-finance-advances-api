package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.services

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.clients.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.exceptions.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.entities.AdvancePaymentEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.entities.AdvanceRecordEntity
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.generalledger.CreatePostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.generalledger.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.request.CreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.request.CreateAdvanceRepaymentRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceRepaymentResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.PagedResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.repositories.AdvancePaymentRepository
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.repositories.AdvanceRecordRepository
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.utils.toPageResponse
import java.util.UUID

@Service
class RecordService(
  private val advanceRecordRepository: AdvanceRecordRepository,
  private val advancePaymentRepository: AdvancePaymentRepository,
  private val insertService: InsertService,
  private val generalLedgerApiClient: GeneralLedgerApiClient,
  val memoryAccountCache: InMemoryAccountCache = InMemoryAccountCache(),
  private val accountResolver: GeneralLedgerAccountResolver,
) {
  private val prisonSubAccountRefAdvances = "1502:ADV"
  private val prisonerSubAccountRefAdvances = "SPENDS"

  private fun postAdvanceCreditToPrisonerTransaction(
    createAdvanceRecordRequest: CreateAdvanceRecordRequest,
    idempotencyKey: UUID,
  ): UUID {
    val prisonAccount = accountResolver.resolveSubAccount(
      createAdvanceRecordRequest.prisonID,
      prisonSubAccountRefAdvances,
      memoryAccountCache,
    )

    val prisonerAccount = accountResolver.resolveSubAccount(
      createAdvanceRecordRequest.prisonNumber,
      prisonerSubAccountRefAdvances,
      memoryAccountCache,
    )

    return generalLedgerApiClient.postTransaction(
      CreateTransactionRequest(
        reference = createAdvanceRecordRequest.reference ?: "",
        description = createAdvanceRecordRequest.comment ?: "",
        timestamp = createAdvanceRecordRequest.createdOn,
        amount = createAdvanceRecordRequest.amount,
        entrySequence = 1,
        postings = listOf(
          CreatePostingRequest(
            subAccountId = prisonAccount,
            type = CreatePostingRequest.Type.DR,
            amount = createAdvanceRecordRequest.amount,
            entrySequence = 1,
          ),
          CreatePostingRequest(
            subAccountId = prisonerAccount,
            type = CreatePostingRequest.Type.CR,
            amount = createAdvanceRecordRequest.amount,
            entrySequence = 2,
          ),
        ),
        legacyTransactionId = createAdvanceRecordRequest.legacyTransactionId,
      ),
      idempotencyKey = idempotencyKey,
    )
  }

  private fun postAdvanceRepaymentTransaction(
    createAdvanceRepaymentRequest: CreateAdvanceRepaymentRequest,
    prisonId: String,
    prisonNumber: String,
    idempotencyKey: UUID,
  ): UUID {
    val prisonAccount = accountResolver.resolveSubAccount(
      prisonId,
      prisonSubAccountRefAdvances,
      memoryAccountCache,
    )

    val prisonerAccount = accountResolver.resolveSubAccount(
      prisonNumber,
      prisonerSubAccountRefAdvances,
      memoryAccountCache,
    )
    return generalLedgerApiClient.postTransaction(
      CreateTransactionRequest(
        reference = "",
        description = createAdvanceRepaymentRequest.description,
        timestamp = createAdvanceRepaymentRequest.createdAt,
        amount = createAdvanceRepaymentRequest.amount,
        entrySequence = 1,
        postings = listOf(
          CreatePostingRequest(
            subAccountId = prisonerAccount,
            type = CreatePostingRequest.Type.DR,
            amount = createAdvanceRepaymentRequest.amount,
            entrySequence = 1,
          ),
          CreatePostingRequest(
            subAccountId = prisonAccount,
            type = CreatePostingRequest.Type.CR,
            amount = createAdvanceRepaymentRequest.amount,
            entrySequence = 2,
          ),
        ),
        legacyTransactionId = createAdvanceRepaymentRequest.legacyTransactionId,
      ),
      idempotencyKey = idempotencyKey,
    )
  }

  fun createAdvanceRecord(
    createAdvanceRecordRequest: CreateAdvanceRecordRequest,
    idempotencyKey: UUID,
  ): AdvanceRecordResponse {
    val transactionGLId = postAdvanceCreditToPrisonerTransaction(
      createAdvanceRecordRequest = createAdvanceRecordRequest,
      idempotencyKey = idempotencyKey,
    )

    val advanceRecordEntity = createAdvanceRecordRequest.toAdvanceRecordEntity()
    val advancePaymentEntity = AdvancePaymentEntity(
      advanceRecordId = advanceRecordEntity.id,
      transactionId = transactionGLId,
      prisonerPostingType = PostingType.CR,
      amount = advanceRecordEntity.amount,
      timestamp = advanceRecordEntity.createdOn,
      createdBy = advanceRecordEntity.createdBy,
    )

    lateinit var advance: AdvanceRecordEntity
    try {
      advance = insertService.saveAdvanceAndPayment(
        advanceRecordEntity,
        advancePaymentEntity,
      )
    } catch (e: DataIntegrityViolationException) {
      val isDuplicateAdvancePayment = e.message?.contains("uc_advance_record_payments_transaction_id") == true
      val isDuplicateAdvance = e.message?.contains("uc_advance_records_legacy_payment_profile_id") == true
      if (isDuplicateAdvancePayment || isDuplicateAdvance) {
        advancePaymentRepository.findByTransactionId(advancePaymentEntity.transactionId)!!
        advance = advanceRecordRepository.getAdvanceRecordsByLegacyPaymentProfileId(advanceRecordEntity.legacyPaymentProfileId)!!
      } else {
        throw e
      }
    }

    return AdvanceRecordResponse.fromEntity(advance)
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

  fun repayAdvance(repaymentRequest: CreateAdvanceRepaymentRequest, advanceId: UUID, idempotencyKey: UUID): AdvanceRepaymentResponse {
    val advance = advanceRecordRepository.getAdvanceRecordEntityById(advanceId)

    // add test
    if (advance == null) throw CustomException("Advance record not found", status = HttpStatus.NOT_FOUND)

    val glTransactionId = postAdvanceRepaymentTransaction(
      createAdvanceRepaymentRequest = repaymentRequest,
      prisonId = advance.prisonID,
      prisonNumber = advance.prisonNumber,
      idempotencyKey = idempotencyKey,
    )

    val advancePayment = advancePaymentRepository.save(
      AdvancePaymentEntity(
        advanceRecordId = advance.id,
        transactionId = glTransactionId,
        prisonerPostingType = PostingType.DR,
        amount = repaymentRequest.amount,
        timestamp = repaymentRequest.createdAt,
        createdBy = repaymentRequest.createdBy,
      ),
    )

    return advancePayment.toResponse(
      legacyTransactionId = repaymentRequest.legacyTransactionId,
      description = repaymentRequest.description,
    )
  }
}
