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
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.enums.AdvanceStatus
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.generalledger.CreatePostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.generalledger.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.request.CreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.request.CreateAdvanceRepaymentRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AccountAdvanceBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvancePrisonerRepaymentResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceRepaymentResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.PagedResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.repositories.AdvancePaymentRepository
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.repositories.AdvanceRecordRepository
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.utils.toPageResponse
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID

@Service
class RecordService(
  private val advanceRecordRepository: AdvanceRecordRepository,
  private val advancePaymentRepository: AdvancePaymentRepository,
  private val insertService: InsertService,
  private val generalLedgerApiClient: GeneralLedgerApiClient,
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
    )

    val prisonerAccount = accountResolver.resolveSubAccount(
      createAdvanceRecordRequest.prisonNumber,
      prisonerSubAccountRefAdvances,
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

  fun postAdvanceRepaymentTransaction(
    createAdvanceRepaymentRequest: CreateAdvanceRepaymentRequest,
    prisonId: String,
    prisonNumber: String,
    idempotencyKey: UUID,
  ): UUID {
    val prisonAccount = accountResolver.resolveSubAccount(
      prisonId,
      prisonSubAccountRefAdvances,
    )

    val prisonerAccount = accountResolver.resolveSubAccount(
      prisonNumber,
      prisonerSubAccountRefAdvances,
    )
    return generalLedgerApiClient.postTransaction(
      CreateTransactionRequest(
        reference = "",
        description = createAdvanceRepaymentRequest.description,
        timestamp = createAdvanceRepaymentRequest.createdAt,
        amount = createAdvanceRepaymentRequest.amount,
        entrySequence = createAdvanceRepaymentRequest.entrySequence.toLong(),
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
      advanceRecord = advanceRecordEntity,
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
      ).advanceRecord
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

    return AdvanceRecordResponse.fromEntity(advance, transactionGLId)
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

  private fun applyPostingType(postingType: PostingType, amount: Long): Long {
    if (postingType == PostingType.CR) return amount
    return -amount
  }

  fun calculateAdvanceBalance(advance: AdvanceRecordEntity): Long {
    // TODO: this might need a refactor for when migrated advances that don't have payments will get added - PFI 1659
    return advancePaymentRepository.findAdvancePaymentEntitiesByAdvanceRecordId(advance.id)
      .fold(0L) { s, it -> s + applyPostingType(it.prisonerPostingType, it.amount) }
  }

  private val ukZone: ZoneId = ZoneId.of("Europe/London")
  private fun getNextRepaymentDay(repaymentStartDate: Instant): LocalDate {
    val localDate: LocalDate = repaymentStartDate.atZone(ukZone).toLocalDate()
    val dayOfWeek: DayOfWeek = localDate.dayOfWeek
    return localDate.with(TemporalAdjusters.next(dayOfWeek))
  }

  fun getAdvanceBalance(advanceId: UUID): AdvanceBalanceResponse {
    val advance = advanceRecordRepository.getAdvanceRecordEntityById(advanceId)
      ?: throw CustomException("Advance record not found", HttpStatus.NOT_FOUND)

    if (advance.status != AdvanceStatus.ACTIVE) {
      return AdvanceBalanceResponse(
        advanceAmount = advance.amount,
        balanceOutstanding = 0,
        weeklyPaymentAmount = advance.repaymentAmount,
        remainingPayments = 0,
        nextPaymentDate = null,
      )
    }

    val outstandingBalance = calculateAdvanceBalance(advance)
    val nextPaymentDate = getNextRepaymentDay(advance.repaymentStartDate)

    return AdvanceBalanceResponse(
      advanceAmount = advance.amount,
      balanceOutstanding = outstandingBalance,
      weeklyPaymentAmount = advance.repaymentAmount,
      remainingPayments = Math.ceilDiv(outstandingBalance, advance.repaymentAmount),
      nextPaymentDate = nextPaymentDate,
    )
  }

  private fun updateAdvanceStatusForRepayment(
    currentAdvanceBalance: Long,
    repaymentRequest: CreateAdvanceRepaymentRequest,
    advance: AdvanceRecordEntity,
  ): AdvanceRecordEntity {
    val updatedAdvanceBalance = currentAdvanceBalance - repaymentRequest.amount
    if (updatedAdvanceBalance == 0L) {
      advance.updatedAt = Instant.now()
      advance.status = AdvanceStatus.REPAID
    }
    if (updatedAdvanceBalance < 0L) {
      throw CustomException("Repayments cannot exceed the balance of the advance record", HttpStatus.BAD_REQUEST)
    }

    return advance
  }

  fun repayAdvance(repaymentRequest: CreateAdvanceRepaymentRequest, advanceId: UUID, idempotencyKey: UUID): AdvanceRepaymentResponse {
    var advance = advanceRecordRepository.getAdvanceRecordEntityById(advanceId)
      ?: throw CustomException("Advance record not found", status = HttpStatus.NOT_FOUND)

    if (advance.status == AdvanceStatus.REPAID || advance.status == AdvanceStatus.WRITTEN_OFF) {
      throw CustomException("Cannot repay an advance with the status of ${advance.status}", HttpStatus.BAD_REQUEST)
    }

    advance = updateAdvanceStatusForRepayment(
      currentAdvanceBalance = calculateAdvanceBalance(advance),
      repaymentRequest,
      advance,
    )

    val glTransactionId = postAdvanceRepaymentTransaction(
      createAdvanceRepaymentRequest = repaymentRequest,
      prisonId = advance.prisonID,
      prisonNumber = advance.prisonNumber,
      idempotencyKey = idempotencyKey,
    )
    var advancePayment: AdvancePaymentEntity
    try {
      advancePayment = insertService.saveAdvanceAndPayment(
        advance,
        AdvancePaymentEntity(
          advanceRecord = advance,
          transactionId = glTransactionId,
          prisonerPostingType = PostingType.DR,
          amount = repaymentRequest.amount,
          timestamp = repaymentRequest.createdAt,
          createdBy = repaymentRequest.createdBy,
        ),
      ).advancePayment
    } catch (e: DataIntegrityViolationException) {
      val isDuplicatedRepayment = e.message?.contains("uc_advance_record_payments_transaction_id") == true
      if (isDuplicatedRepayment) {
        advancePayment = advancePaymentRepository.findByTransactionId(glTransactionId)!!
      } else {
        throw e
      }
    }

    return advancePayment.toResponse(
      legacyTransactionId = repaymentRequest.legacyTransactionId,
      description = repaymentRequest.description,
      entrySequence = repaymentRequest.entrySequence,
    )
  }

  fun getAccountAdvanceBalance(prisonNumber: String): AccountAdvanceBalanceResponse {
    val allActiveAdvances = advanceRecordRepository.findAdvanceRecordEntitiesByPrisonNumberAndStatus(prisonNumber, AdvanceStatus.ACTIVE)

    val totalActiveAdvanceAmount = allActiveAdvances.sumOf { it.amount }
    val totalActiveOutstandingBalance = allActiveAdvances.sumOf { calculateAdvanceBalance(it) }
    val totalActiveWeeklyPaymentAmount = allActiveAdvances.sumOf { it.repaymentAmount }

    return AccountAdvanceBalanceResponse(
      advancesAmount = totalActiveAdvanceAmount,
      balancesOutstanding = totalActiveOutstandingBalance,
      weeklyPaymentAmount = totalActiveWeeklyPaymentAmount,
    )
  }

  fun getPrisonerRepayments(advanceId: UUID, pageNumber: Int, pageSize: Int): PagedResponse<AdvancePrisonerRepaymentResponse> {
    val zeroIndexedPage: Int = pageNumber - 1
    val pagedRequest = PageRequest.of(
      zeroIndexedPage,
      pageSize,
      Sort.by(
        Sort.Order.desc("timestamp"),
        Sort.Order.desc("id"),
      ),
    )

    advanceRecordRepository.getAdvanceRecordEntityById(advanceId) ?: throw CustomException("No record found with id $advanceId", HttpStatus.NOT_FOUND)

    val result = advancePaymentRepository.findAdvancePaymentEntitiesByAdvanceRecordIdAndPrisonerPostingType(
      advanceRecordId = advanceId,
      prisonerPostingType = PostingType.DR,
      pageable = pagedRequest,
    )

    return result.toPageResponse { content ->
      content.map { it.toPrisonerRepaymentResponse() }
    }
  }
}
